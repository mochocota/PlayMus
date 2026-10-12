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
    // tandas. Detecta el archivo melodix_import.json para importar
    // el historial, y si la biblioteca está vacía intenta el
    // AUTO-IMPORT (file_id integrado o guardado) para que los
    // dispositivos nuevos carguen todo sin reenviar nada.
    // Si otro dispositivo está usando el bot (HTTP 409), no falla:
    // avisa y continúa con carátulas y géneros.
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
                var manualImportFid: String? = null
                var conflict = false
                var offset = TelegramConfig.offset(context)
                var processed = 0
                val CAP = 1500

                // ---------- AUTO-IMPORT ----------
                // Si la biblioteca está vacía (dispositivo nuevo o datos
                // borrados), carga el archivo de importación integrado
                // en la app o recordado por este teléfono.
                if (_songs.value.isEmpty()) {
                    val autoId = TelegramConfig.autoImportFileId(context)
                    if (autoId.isNotBlank()) {
                        val (n, err) = importFromJson(client, autoId, result, known)
                        imported += n
                        if (err != null) importError = "auto: $err"
                    }
                }

                while (processed < CAP) {
                    val updates = try {
                        client.getUpdates(offset, if (processed == 0) 10 else 0)
                    } catch (e: Exception) {
                        val emsg = e.message ?: ""
                        if (processed == 0) {
                            if (emsg.contains("409")) {
                                // Otro dispositivo está usando el bot ahora mismo.
                                // No es fatal: seguimos con carátulas y géneros.
                                conflict = true
                                break
                            }
                            return@withContext "Error al conectar: ${e.message}"
                        }
                        break
                    }
                    if (updates.isEmpty()) break

                    var batchMax = -1L
                    for (u in updates) {
                        batchMax = maxOf(batchMax, u.optLong("update_id", -1L))

                        // ¿Es el archivo de importación del historial?
                        val importFileId = extractImportFileId(u)
                        if (importFileId != null) {
                            // Recuerda este file_id en el teléfono y muéstralo
                            // para que pueda pegarse en DEFAULT_IMPORT_FILE_ID
                            TelegramConfig.saveAutoImportFileId(context, importFileId)
                            manualImportFid = importFileId
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
                if (conflict) sb.append(" · Otro dispositivo estaba sincronizando; el canal se revisará luego")
                if (manualImportFid != null) sb.append(" · file_id: $manualImportFid")
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

    // Descarga y parsea el JSON, y añade las canciones que falten.
    // Valida que la descarga sea realmente un JSON (evita interpretar
    // una canción M4A como archivo de importación).
    private fun importFromJson(
        client: TelegramClient,
        fileId: String,
        result: MutableList<Song>,
        known: MutableSet<String>
    ): Pair<Int, String?> {
        return try {
            val url = client.fileUrl(client.getFilePath(fileId))
            val body = URL(url).readText()
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("[")) {
                return 0 to "el file_id no apunta al JSON (vuelve a enviar melodix_import.json al bot)"
            }
            val arr = JSONArray(trimmed)
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
                    s.title.isNotBlank() && s.title.length >=
