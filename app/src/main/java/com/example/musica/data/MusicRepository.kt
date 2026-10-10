package com.example.musica.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.Dispatchers
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

    private val mutex = Mutex()
    private lateinit var context: Context

    // ============================================================
    // PARÁMETROS DE VELOCIDAD DEL ESCANEO
    // SCAN_PARALLEL = cuántas canciones se analizan A LA VEZ
    // SCAN_BATCH = cuántas se procesan por tanda
    // ============================================================
    private const val SCAN_PARALLEL = 6
    private const val SCAN_BATCH = 24
    private const val ONLINE_PARALLEL = 4
    private const val ONLINE_BATCH = 8

    fun init(ctx: Context) {
        context = ctx.applicationContext
        coversDir().mkdirs()
        load()
        reclassifyLite()
        resetOnlineTries()
    }

    // ---------- Carátulas ----------
    private fun coversDir() = File(context.filesDir, "covers")
    fun coverFile(id: Int) = File(coversDir(), "$id.img")

    fun pendingCount(): Int = _songs.value.count { it.fromTelegram && !it.metaDone }

    fun pendingOnlineCount(): Int = _songs.value.count {
        it.fromTelegram && it.genre == "Otros" && !it.scanned &&
                it.artist.isNotBlank() && it.artist != "Desconocido" &&
                it.title.isNotBlank() && it.title.length >= 3
    }

    // ---------- Consultas ----------
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

    // ---------- Sincronización con Telegram ----------
    suspend fun sync(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val token = TelegramConfig.token(context)
            if (token.isBlank()) return@withContext "Primero configura tu bot en 'Canal de Telegram'"

            val client = TelegramClient(token)
            client.deleteWebhook()

            val updates = try {
                client.getUpdates(TelegramConfig.offset(context), timeoutSec = 10)
            } catch (e: Exception) {
                return@withContext "Error al conectar: ${e.message}"
            }

            val result = _songs.value.toMutableList()
            var added = 0
            var skipped = 0
            var fixed = 0

            for (u in updates) {
                val a = TelegramClient.extractAudio(u) ?: continue

                val existingIdx = result.indexOfFirst { it.fileId == a.fileId }
                if (existingIdx >= 0) {
                    val old = result[existingIdx]
                    val hasTag = genreFromTags(a.caption) != null
                    if (old.genre == "Otros" || hasTag) {
                        val newGenre = detectGenre(a.caption, a.fileName, null) ?: old.genre
                        if (newGenre != old.genre || a.caption != old.caption) {
                            result[existingIdx] = old.copy(
                                caption = a.caption.ifBlank { old.caption },
                                fileName = a.fileName.ifBlank { old.fileName },
                                genre = newGenre
                            )
                            fixed++
                        }
                    }
                    continue
                }

                if (a.fileSize > 19_500_000) { skipped++; continue }
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
            _songs.value = result
            save()

            val maxId = updates.maxOfOrNull { it.optLong("update_id", -1L) } ?: -1L
            if (maxId >= 0) {
                TelegramConfig.saveOffset(context, maxId + 1)
                try { client.getUpdates(maxId + 1, timeoutSec = 0) } catch (_: Exception) {}
            }

            // 1) Metadatos locales (carátula, álbum, género ID3) — en paralelo
            val covers = scanLocked(limit = SCAN_BATCH)
            // 2) Re-clasificar lo que quedó como "Otros"
            fixed += reclassifyLite()
            // 3) Buscar géneros online (iTunes / Deezer) — en paralelo
            val online = enrichLocked(limit = ONLINE_BATCH)

            val sb = StringBuilder("Sincronizado: $added canciones nuevas")
            if (skipped > 0) sb.append(" · $skipped omitidas (>20MB)")
            if (covers > 0) sb.append(" · $covers carátulas añadidas")
            if (online > 0) sb.append(" · $online géneros por internet")
            if (fixed > 0) sb.append(" · $fixed géneros corregidos")
            if (updates.isEmpty() && added == 0 && covers == 0 && online == 0 && fixed == 0)
                sb.append(" (sin cambios)")
            sb.toString()
        }
    }

    suspend fun scanPending(limit: Int = SCAN_BATCH): Int = mutex.withLock { scanLocked(limit) }

    suspend fun enrichOnline(limit: Int = ONLINE_BATCH): Int = mutex.withLock { enrichLocked(limit) }

    // ============================================================
    // ESCANEO EN PARALELO: analiza hasta SCAN_PARALLEL canciones
    // a la vez (antes era de una en una). Extrae carátula, título,
    // artista, álbum y género de los metadatos ID3.
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

    // Analiza UNA canción (se ejecuta en paralelo con otras)
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
                newSong = newSong.copy(
                    title = t?.takeIf { it.isNotBlank() } ?: newSong.title,
                    artist = a?.takeIf { it.isNotBlank() } ?: newSong.artist,
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

    // ============================================================
    // BÚSQUEDA ONLINE EN PARALELO (iTunes primero, Deezer respaldo)
    // ============================================================
    private suspend fun enrichLocked(limit: Int): Int = withContext(Dispatchers.IO) {
        val candidates = _songs.value.filter {
            it.fromTelegram && it.genre == "Otros" && !it.scanned &&
                    it.artist.isNotBlank() && it.artist != "Desconocido" &&
                    it.title.isNotBlank() && it.title.length >= 3
        }.take(limit)
        if (candidates.isEmpty()) return@withContext 0

        val sem = Semaphore(ONLINE_PARALLEL)
        val results = ConcurrentHashMap<Int, String?>()

        val jobs = candidates.map { song ->
            launch {
                sem.withPermit {
                    results[song.id] = lookupGenreOnline(song.title, song.artist)
                }
            }
        }
        jobs.joinAll()

        if (results.isEmpty()) return@withContext 0
        val got = results.values.count { it != null }
        _songs.value = _songs.value.map { s ->
            if (results.containsKey(s.id)) s.copy(scanned = true, genre = results[s.id] ?: s.genre)
            else s
        }
        save()
        got
    }

    // Consulta iTunes primero; si no hay suerte, Deezer
    private fun lookupGenreOnline(title: String, artist: String): String? {
        // ---------- 1) iTunes Search API ----------
        try {
            val q = URLEncoder.encode("$artist $title", "UTF-8")
            val conn = URL("https://itunes.apple.com/search?term=$q&entity=song&limit=1")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MusicaApp/1.0")
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONObject(body).optJSONArray("results")
                if (arr != null && arr.length() > 0) {
                    val hit = arr.getJSONObject(0)
                    val t = hit.optString("trackName", "")
                    val g = hit.optString("primaryGenreName", "")
                    if (g.isNotBlank() && titleMatches(t, title)) {
                        mappedGenre(g)?.let { return it }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {}

        // ---------- 2) Deezer (respaldo) ----------
        try {
            val q = URLEncoder.encode("artist:\"$artist\" track:\"$title\"", "UTF-8")
            val conn = URL("https://api.deezer.com/search?q=$q&limit=1")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MusicaApp/1.0")
                val body = conn.inputStream.bufferedReader().readText()
                val data = JSONObject(body).optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val track = data.getJSONObject(0)
                    val t = track.optString("title_short", track.optString("title", ""))
                    if (titleMatches(t, title)) {
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
                                            mappedGenre(gname)?.let { return it }
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

    // Comprueba que el título devuelto por internet se parece al buscado
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

    // ============================================================
    // DETECCIÓN DE GÉNERO — orden de prioridad:
    // 1. Hashtags (#pop, #rock…) en el caption o nombre del archivo
    // 2. Texto "género: X" en el caption
    // 3. Etiqueta ID3 del MP3 (incluye tabla numérica ID3v1)
    // 4. Palabras del género en el nombre del archivo (sin #)
    // (Si nada funciona, después se busca online en iTunes/Deezer)
    // ============================================================
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

    // Tabla oficial ID3v1 (para etiquetas que llegan como número, ej: "13" = Pop)
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

    // Sinónimos extra que traen iTunes/Deezer y no están en el mapa general
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

    // Re-clasifica (sin internet) las canciones guardadas como "Otros"
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

    // Da una nueva oportunidad de búsqueda online en cada arranque de la app
    private fun resetOnlineTries() {
        val updated = _songs.value.map {
            if (it.fromTelegram && it.genre == "Otros" && it.scanned) it.copy(scanned = false) else it
        }
        if (updated != _songs.value) {
            _songs.value = updated
            save()
        }
    }

    // ---------- Almacenamiento local (JSON) ----------
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
