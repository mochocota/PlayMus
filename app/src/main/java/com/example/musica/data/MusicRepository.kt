package com.example.musica.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object MusicRepository {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    // true cuando la biblioteca terminó de cargar en segundo plano
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val mutex = Mutex()
    // Señaliza que la carga inicial terminó (evita carreras)
    private val loadedGate = CompletableDeferred<Unit>()
    // Scope propio para la carga inicial (no bloquea la app al abrir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var context: Context

    private const val SCAN_PARALLEL = 6
    private const val SCAN_BATCH = 24
    private const val ONLINE_PARALLEL = 4
    private const val ONLINE_BATCH = 8
    private val MAX_ONLINE_ATTEMPTS = 3
    private val attempts = ConcurrentHashMap<Int, Int>()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        coversDir().mkdirs()
        // La carga del JSON (grande con miles de canciones) se hace en
        // segundo plano para que la app abra al instante
        scope.launch {
            load()
            loadAttempts()
            reclassifyLite()
            loadedGate.complete(Unit)
            _ready.value = true
        }
    }

    private fun coversDir() = File(context.filesDir, "covers")
    fun coverFile(id: Int) = File(coversDir(), "$id.img")

    fun pendingCount(): Int = _songs.value.count { it.fromTelegram && !it.metaDone }

    fun pendingOnlineCount(): Int = _songs.value.count { s ->
        s.fromTelegram && s.genre == "Otros" &&
                (attempts[s.id] ?: 0) < MAX_ONLINE_ATTEMPTS &&
                s.title.isNotBlank() && s.title.length >= 3
    }

    fun genresWithCount(): List<Pair<String, Int>> =
        _songs.value.groupingBy { it.genre }.eachCount().toList().sortedBy { it.first.lowercase() }

    fun songsByGenre(genre: String): List<Song> =
        if (genre == "Todas") _songs.value else _songs.value.filter { it.genre == genre }

    fun albums(): List<Pair<String, List<Song>>> =
        _songs.value.groupBy { it.album.ifBlank { "Sencillos" } }.toList()
            .sortedBy { it.first.lowercase() }

    fun artistCount(): Int = _songs.value.map { it.artist }.distinct().size
    fun albumCount(): Int = albums().size

    fun updateGenre(songId: Int, genre: String) {
        _songs.value = _songs.value.map {
            if (it.id == songId) it.copy(genre = genre) else it
        }
        save()
    }

    // ============================================================
    // SINCRONIZACIÓN: procesa TODOS los mensajes pendientes en
    // tandas (hasta 1500 por pulsación). También detecta el
    // archivo melodix_import.json para importar el historial.
    // Espera a que la biblioteca cargue antes de trabajar.
    // ============================================================
    suspend fun sync(): String {
        loadedGate.await()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val token = TelegramConfig.token(context)
                if (token.isBlank()) return@withContext "Primero configura tu bot en 'Canal de Telegram'"

                val client = TelegramClient(token)
                client.deleteWebhook()

                val known = _songs.value.map { it.fileId }.toMutableSet()
                val result = _songs.value.toMutableList()
                var added = 0
                var skipped = 0
                var fixed = 0
                var imported = 0
                var importError: String? = null
                var offset = TelegramConfig.offset(context)
                var processed = 0
                val CAP = 1500

                while (processed < CAP) {
                    val updates = try {
                        client.getUpdates(offset, if (processed == 0) 10 else 0)
                    } catch (e: Exception) {
                        if (processed == 0) return@withContext "Error al conectar: ${e.message}"
                        break
                    }
                    if (updates.isEmpty()) break

                    var batchMax = -1L
                    for (u in updates) {
                        batchMax = maxOf(batchMax, u.optLong("update_id", -1L))

                        // ¿Es el archivo de importación del historial?
                        val importFileId = extractImportFileId(u)
                        if (importFileId != null) {
                            val (n, err) = importFromJson(client, importFileId, result, known)
                            imported += n
                            if (err != null) importError = err
                            continue
                        }

                        val a = TelegramClient.extractAudio(u) ?: continue

                        if (a.fileId in known) {
                            val idx = result.indexOfFirst { it.fileId == a.fileId }
                            if (idx >= 0) {
                                val old = result[idx]
                                val hasTag = genreFromTags(a.caption) != null
                                if (old.genre == "Otros" || hasTag) {
                                    val newGenre = detectGenre(a.caption, a.fileName, null) ?: old.genre
                                    if (newGenre != old.genre || a.caption != old.caption) {
                                        result[idx] = old.copy(
                                            caption = a.caption.ifBlank { old.caption },
                                            fileName = a.fileName.ifBlank { old.fileName },
                                            genre = newGenre
                                        )
                                        fixed++
                                    }
                                }
                            }
                            continue
                        }

                        if (a.fileSize > 19_500_000) { skipped++; continue }
                        known.add(a.fileId)
                        result.add(
                            0, Song(
                                id = a.fileId.hashCode(),
                                title = a.title, artist = a.artist, album = "",
                                durationSec = a.duration, url = "",
                                fileId = a.fileId,
                                genre = detectGenre(a.caption, a.fileName, null) ?: "Otros",
                                fromTelegram = true,
                                caption = a.caption,
                                fileName = a.fileName
                            )
                        )
                        added++
                    }

                    offset = batchMax + 1
                    processed += updates.size

                    // Guarda progreso parcial (clave en importaciones grandes)
                    if (added + imported > 0) {
                        _songs.value = result.toList()
                        save()
                    }
                    TelegramConfig.saveOffset(context, offset)
                }

                _songs.value = result
                save()
                TelegramConfig.saveOffset(context, offset)

                val covers = scanLocked(limit = SCAN_BATCH)
                fixed += reclassifyLite()
                val online = enrichLocked(limit = ONLINE_BATCH)

                val sb = StringBuilder()
                if (imported > 0) sb.append("Importadas: $imported del historial")
                if (added > 0) {
                    if (sb.isNotEmpty()) sb.append(" · ")
                    sb.append("Sincronizado: $added nuevas")
                }
                if (skipped > 0) sb.append(" · $skipped omitidas (>20MB)")
                if (covers > 0) sb.append(" · $covers carátulas añadidas")
                if (online > 0) sb.append(" · $online géneros por internet")
                if (fixed > 0) sb.append(" · $fixed géneros corregidos")
                if (importError != null) sb.append(" · Error importación: $importError")
                if (sb.isEmpty()) sb.append("(sin cambios)")
                sb.toString()
            }
        }
    }

    // Detecta el documento melodix_import.json enviado al bot
    private fun extractImportFileId(update: JSONObject): String? {
        val msg = update.optJSONObject("message") ?: return null
        val doc = msg.optJSONObject("document") ?: return null
        val name = doc.optString("file_name", "")
        if (!name.equals("melodix_import.json", ignoreCase = true)) return null
        val fid = doc.optString("file_id", "")
        return if (fid.isBlank()) null else fid
    }

    // Descarga y parsea el JSON, y añade las canciones que falten
    private fun importFromJson(
        client: TelegramClient,
        fileId: String,
        result: MutableList<Song>,
        known: MutableSet<String>
    ): Pair<Int, String?> {
        return try {
            val url = client.fileUrl(client.getFilePath(fileId))
            val body = URL(url).readText()
            val arr = JSONArray(body)
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fid = o.optString("fileId", "")
                if (fid.isBlank() || fid in known) continue
                val caption = o.optString("caption", "")
                val fileName = o.optString("fileName", "")
                known.add(fid)
                result.add(
                    0, Song(
                        id = fid.hashCode(),
                        title = o.optString("title", "Sin título").ifBlank { "Sin título" },
                        artist = o.optString("artist", "Desconocido").ifBlank { "Desconocido" },
                        album = o.optString("album", ""),
                        durationSec = o.optInt("durationSec", 0),
                        url = "", fileId = fid,
                        genre = detectGenre(caption, fileName, null) ?: "Otros",
                        fromTelegram = true,
                        caption = caption, fileName = fileName
                    )
                )
                n++
            }
            n to null
        } catch (e: Exception) {
            0 to (e.message ?: "desconocido")
        }
    }

    suspend fun scanPending(limit: Int = SCAN_BATCH): Int {
        loadedGate.await()
        return mutex.withLock { scanLocked(limit) }
    }

    suspend fun enrichOnline(limit: Int = ONLINE_BATCH): Int {
        loadedGate.await()
        return mutex.withLock { enrichLocked(limit) }
    }

    // ============================================================
    // ESCANEO EN PARALELO de metadatos ID3 (carátula, título,
    // artista, álbum, género). Hasta SCAN_PARALLEL a la vez.
    // ============================================================
    private suspend fun scanLocked(limit: Int): Int = withContext(Dispatchers.IO) {
        val token = TelegramConfig.token(context)
        if (token.isBlank()) return@withContext 0

        val pending = _songs.value.filter { it.fromTelegram && !it.metaDone }.take(limit)
        if (pending.isEmpty()) return@withContext 0

        val client = TelegramClient(token)
        val sem = Semaphore(SCAN_PARALLEL)
        val results = ConcurrentHashMap<Int, Song>()

        val jobs = pending.map { song ->
            launch {
                sem.withPermit {
                    results[song.id] = scanOne(client, song)
                }
            }
        }
        jobs.joinAll()

        if (results.isEmpty()) return@withContext 0
        val covers = results.values.count { it.coverPath.isNotBlank() }
        _songs.value = _songs.value.map { s -> results[s.id] ?: s }
        save()
        covers
    }

    private fun scanOne(client: TelegramClient, song: Song): Song {
        var newSong = song.copy(metaDone = true)
        try {
            val url = client.fileUrl(client.getFilePath(song.fileId))
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(url)
                val t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val al = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val g = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                val art = r.embeddedPicture

                if (art != null && art.isNotEmpty()) {
                    try {
                        val f = coverFile(song.id)
                        f.parentFile?.mkdirs()
                        f.writeBytes(art)
                        newSong = newSong.copy(coverPath = f.absolutePath)
                    } catch (_: Exception) {}
                }

                var finalTitle = t?.takeIf { it.isNotBlank() } ?: newSong.title
                var finalArtist = a?.takeIf { it.isNotBlank() } ?: newSong.artist

                if (finalArtist.isBlank() || finalArtist == "Desconocido") {
                    if (finalTitle.contains(" - ")) {
                        val parts = finalTitle.split(" - ", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank()) {
                            finalArtist = parts[0].trim()
                            finalTitle = parts[1].trim()
                        }
                    }
                }

                newSong = newSong.copy(
                    title = finalTitle,
                    artist = finalArtist,
                    album = al?.takeIf { it.isNotBlank() } ?: newSong.album,
                    genre = if (newSong.genre == "Otros")
                        (detectGenre(newSong.caption, newSong.fileName, g) ?: "Otros")
                    else newSong.genre
                )
            } finally {
                r.release()
            }
        } catch (_: Exception) {}
        return newSong
    }

    private data class OnlineHit(
        val genre: String,
        val artist: String?,
        val album: String?
    )

    private suspend fun enrichLocked(limit: Int): Int = withContext(Dispatchers.IO) {
        val candidates = _songs.value.filter { s ->
            s.fromTelegram && s.genre == "Otros" &&
                    (attempts[s.id] ?: 0) < MAX_ONLINE_ATTEMPTS &&
                    s.title.isNotBlank() && s.title.length >= 3
        }.take(limit)
        if (candidates.isEmpty()) return@withContext 0

        val sem = Semaphore(ONLINE_PARALLEL)
        val results = ConcurrentHashMap<Int, OnlineHit>()

        val jobs = candidates.map { song ->
            launch {
                sem.withPermit {
                    attempts[song.id] = (attempts[song.id] ?: 0) + 1
                    val hit = lookupGenreOnline(song.title, song.artist)
                    if (hit != null) results[song.id] = hit
                }
            }
        }
        jobs.joinAll()
        saveAttempts()

        if (results.isEmpty()) return@withContext 0
        val got = results.size
        _songs.value = _songs.value.map { s ->
            val hit = results[s.id] ?: return@map s
            var updated = s.copy(genre = hit.genre, scanned = true)
            if ((s.artist.isBlank() || s.artist == "Desconocido") &&
                !hit.artist.isNullOrBlank()) {
                updated = updated.copy(artist = hit.artist)
            }
            if (s.album.isBlank() && !hit.album.isNullOrBlank()) {
                updated = updated.copy(album = hit.album)
            }
            updated
        }
        save()
        got
    }

    private fun cleanForSearch(raw: String): String = raw
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("\\[.*?\\]"), " ")
        .replace(Regex("\\b(official|video|audio|letra|lyrics|lyric|hd|hq|4k|remaster(ed)?|vizualizer|visualizer)\\b.*", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\b(ft|feat|featuring|con)\\..*$", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun lookupGenreOnline(titleRaw: String, artistRaw: String): OnlineHit? {
        var title = cleanForSearch(titleRaw)
        var artist = artistRaw.takeIf { it.isNotBlank() && it != "Desconocido" } ?: ""

        if (artist.isBlank() && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) {
                artist = parts[0].trim()
                title = parts[1].trim()
            }
        }
        if (title.isBlank()) return null

        try {
            val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
            val q = URLEncoder.encode(term, "UTF-8")
            val conn = URL("https://itunes.apple.com/search?term=$q&entity=song&limit=3")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MusicaApp/1.0")
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONObject(body).optJSONArray("results")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val hit = arr.getJSONObject(i)
                        val t = hit.optString("trackName", "")
                        val g = hit.optString("primaryGenreName", "")
                        if (g.isNotBlank() && titleMatches(t, title)) {
                            return OnlineHit(
                                genre = mappedGenre(g) ?: g,
                                artist = hit.optString("artistName", "").ifBlank { null },
                                album = hit.optString("collectionName", "").ifBlank { null }
                            )
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {}

        try {
            val q = if (artist.isNotBlank())
                URLEncoder.encode("artist:\"$artist\" track:\"$title\"", "UTF-8")
            else
                URLEncoder.encode("track:\"$title\"", "UTF-8")
            val conn = URL("https://api.deezer.com/search?q=$q&limit=3")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MusicaApp/1.0")
                val body = conn.inputStream.bufferedReader().readText()
                val data = JSONObject(body).optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val track = data.getJSONObject(i)
                        val t = track.optString("title_short", track.optString("title", ""))
                        if (!titleMatches(t, title)) continue
                        val albumId = track.optJSONObject("album")?.optLong("id") ?: 0L
                        if (albumId > 0) {
                            try {
                                val c2 = URL("https://api.deezer.com/album/$albumId")
                                    .openConnection() as HttpURLConnection
                                try {
                                    c2.connectTimeout = 8000
                                    c2.readTimeout = 8000
                                    c2.setRequestProperty("User-Agent", "MusicaApp/1.0")
                                    val album = JSONObject(c2.inputStream.bufferedReader().readText())
                                    val genres = album.optJSONObject("genres")?.optJSONArray("data")
                                    if (genres != null && genres.length() > 0) {
                                        val gname = genres.getJSONObject(0).optString("name", "")
                                        if (gname.isNotBlank()) {
                                            val artistName = track.optJSONObject("artist")
                                                ?.optString("name", "")?.ifBlank { null }
                                            return OnlineHit(
                                                genre = mappedGenre(gname) ?: gname,
                                                artist = artistName,
                                                album = album.optString("title", "").ifBlank { null }
                                            )
                                        }
                                    }
                                } finally {
                                    c2.disconnect()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {}
        return null
    }

    private fun titleMatches(onlineTitle: String, localTitle: String): Boolean {
        val a = norm(onlineTitle)
        val b = norm(localTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        val wa = a.split(" ").filter { it.length > 2 }
        val wb = b.split(" ").filter { it.length > 2 }
        if (wa.isEmpty() || wb.isEmpty()) return false
        val common = wa.count { it in wb }
        return common >= 1 && common >= minOf(wa.size, wb.size) / 2
    }

    private fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().trim()

    private fun detectGenre(caption: String, fileName: String, id3: String?): String? {
        genreFromTags(caption)?.let { return it }
        genreFromTags(fileName)?.let { return it }
        captionGenreLabel(caption)?.let { return it }
        mappedGenre(id3)?.let { return it }
        fileNameKeyword(fileName)?.let { return it }
        return null
    }

    private fun genreFromTags(text: String): String? {
        val tags = Regex("#\\s*([\\p{L}]+)").findAll(text).map { norm(it.groupValues[1]) }.toList()
        for (t in tags) for ((k, v) in genreMap) if (t == k) return v
        for (t in tags) for ((k, v) in genreMap) if (k.length > 3 && (t.contains(k) || k.contains(t))) return v
        return null
    }

    private fun captionGenreLabel(caption: String): String? {
        val after = Regex("g[eé]nero\\s*:\\s*([\\p{L} ]+)").find(caption)?.groupValues?.get(1)?.trim()
            ?: return null
        val n = norm(after)
        for ((k, v) in genreMap) if (k in n) return v
        return after.replaceFirstChar { it.uppercase() }
    }

    private fun fileNameKeyword(fileName: String): String? {
        val tokens = Regex("[^\\p{L}]+").split(norm(fileName)).filter { it.length >= 3 }
        for (t in tokens) {
            for ((k, v) in genreMap) {
                if (k.contains(" ")) continue
                val nk = norm(k)
                if (t == nk || t == nk + "s" || (nk.length >= 5 && t.contains(nk))) return v
            }
        }
        return null
    }

    private val id3v1Names = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
        "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
        "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
        "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient",
        "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical",
        "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
        "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
        "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
        "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap",
        "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
        "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
        "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll",
        "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing", "Fast-Fusion",
        "Bebop", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
        "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
        "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour",
        "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony",
        "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club",
        "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
        "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House",
        "Dance Hall"
    )

    private val extraGenreWords = listOf(
        "latin" to "Latino", "latino" to "Latino",
        "country" to "Country", "soul" to "Soul", "funk" to "Funk",
        "blues" to "Blues", "indie" to "Indie",
        "alternative" to "Alternativo", "alternativo" to "Alternativo",
        "world" to "Música del Mundo", "k-pop" to "K-Pop", "j-pop" to "J-Pop",
        "new age" to "New Age", "easy listening" to "Instrumental",
        "children" to "Infantil"
    )

    private fun mappedGenre(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val num = norm(raw)
        val resolved = if (num.all { it.isDigit() }) {
            val idx = num.toIntOrNull() ?: return null
            if (idx in id3v1Names.indices) id3v1Names[idx] else return null
        } else raw
        val n = norm(resolved)
        if (n == "other" || n == "otros" || n == "unknown" || n == "desconocido") return null
        for ((k, v) in genreMap) if (k in n) return v
        for ((k, v) in extraGenreWords) if (k in n) return v
        return resolved.split("/", "(", "-").first().trim()
            .replaceFirstChar { it.uppercase() }.ifBlank { null }
    }

    private fun reclassifyLite(): Int {
        var count = 0
        val updated = _songs.value.map { s ->
            if (s.genre == "Otros" && (s.caption.isNotBlank() || s.fileName.isNotBlank())) {
                val g = detectGenre(s.caption, s.fileName, null)
                if (g != null && g != "Otros") count++
                s.copy(genre = g ?: s.genre)
            } else s
        }
        if (updated != _songs.value) {
            _songs.value = updated
            save()
        }
        return count
    }

    private fun attemptsFile() = File(context.filesDir, "online_attempts.json")

    private fun saveAttempts() {
        try {
            val o = JSONObject()
            attempts.forEach { (k, v) -> o.put(k.toString(), v) }
            attemptsFile().writeText(o.toString())
        } catch (_: Exception) {}
    }

    private fun loadAttempts() {
        try {
            val f = attemptsFile()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            o.keys().forEach { k -> attempts[k.toInt()] = o.optInt(k, 0) }
        } catch (_: Exception) {}
    }

    private fun file() = File(context.filesDir, "library.json")

    private fun save() {
        try {
            val arr = JSONArray()
            _songs.value.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id).put("title", s.title).put("artist", s.artist)
                        .put("album", s.album).put("durationSec", s.durationSec)
                        .put("fileId", s.fileId).put("genre", s.genre)
                        .put("fromTelegram", s.fromTelegram).put("scanned", s.scanned)
                        .put("coverPath", s.coverPath).put("metaDone", s.metaDone)
                        .put("caption", s.caption).put("fileName", s.fileName)
                )
            }
            file().writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Song(
                    id = o.optInt("id"), title = o.optString("title"),
                    artist = o.optString("artist"), album = o.optString("album"),
                    durationSec = o.optInt("durationSec"), url = "",
                    fileId = o.optString("fileId"),
                    genre = o.optString("genre", "Otros"),
                    fromTelegram = o.optBoolean("fromTelegram"),
                    scanned = o.optBoolean("scanned"),
                    coverPath = o.optString("coverPath", ""),
                    metaDone = o.optBoolean("metaDone"),
                    caption = o.optString("caption", ""),
                    fileName = o.optString("fileName", "")
                )
            }
            _songs.value = list
        } catch (_: Exception) {}
    }
}
