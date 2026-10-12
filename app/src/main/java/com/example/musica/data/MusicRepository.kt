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
import kotlin.math.abs

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
        // Carga en segundo plano, a prueba de fallos: aunque algo
        // explote (JSON corrupto, memoria), ready SIEMPRE se activa
        // y la app nunca se queda con el círculo girando
        scope.launch {
            try {
                load()
                if (_songs.value.isEmpty()) loadSeedCatalog()
                loadAttempts()
                reclassifyLite()
            } catch (t: Throwable) {
                // Continúa sin biblioteca antes que quedarse colgado
                t.printStackTrace()
            } finally {
                loadedGate.complete(Unit)
                _ready.value = true
            }
        }
    }

    private fun coversDir() = File(context.filesDir, "covers")
    fun coverFile(id: Int) = File(coversDir(), "$id.img")

    // Archivo de la biblioteca (para compartirlo como semilla)
    fun libraryFile(): File = file()

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
    // CATÁLOGO SEMILLA (res/raw/seed_catalog.json): si existe y la
    // biblioteca está vacía, carga todas las canciones al instante.
    // A prueba de fallos: un archivo dañado no bloquea la app.
    // ============================================================
    private fun loadSeedCatalog() {
        try {
            val resId = context.resources.getIdentifier(
                "seed_catalog", "raw", context.packageName
            )
            if (resId == 0) return
            val body = context.resources.openRawResource(resId)
                .bufferedReader().use { it.readText() }
            val list = _songs.value.toMutableList()
            val known = list.map { it.fileId }.toMutableSet()
            val n = parseAndMerge(body, list, known)
            if (n > 0) {
                _songs.value = list
                save()
            }
        } catch (_: Throwable) {}
    }

    // ============================================================
    // SINCRONIZACIÓN: mensajes pendientes + importación + REPARACIÓN
    // de file_id (los de cuenta de usuario no funcionan con bots;
    // los reenvíos al bot traen file_id válidos que se intercambian
    // en las entradas existentes, conservando carátulas y favoritos).
    // Si otro dispositivo está usando el bot (HTTP 409), no falla.
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
                var repaired = 0
                var importError: String? = null
                var manualImportFid: String? = null
                var conflict = false
                var offset = TelegramConfig.offset(context)
                var processed = 0
                val CAP = 1500

                // ---------- AUTO-IMPORT ----------
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
                            TelegramConfig.saveAutoImportFileId(context, importFileId)
                            manualImportFid = importFileId
                            val (n, err) = importFromJson(client, importFileId, result, known)
                            imported += n
                            if (err != null) importError = err
                            // Envía el file_id al chat del usuario por Telegram:
                            // ahí copiar es fácil y fiable
                            val chatId = u.optJSONObject("message")
                                ?.optJSONObject("chat")?.optLong("id") ?: 0L
                            if (chatId != 0L) {
                                TelegramConfig.saveImportChatId(context, chatId)
                                try {
                                    client.sendMessage(chatId,
                                        "Melodix ✓ Importación procesada ($n canciones nuevas)\n\n" +
                                        "file_id para integrar en la app (pégalo en DEFAULT_IMPORT_FILE_ID):\n\n" +
                                        importFileId)
                                } catch (_: Exception) {}
                            }
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

                        // ---------- REPARACIÓN DE FILE_ID ----------
                        // El mismo audio reenviado al bot llega con un
                        // file_id válido. Si coincide con una entrada
                        // existente, se reemplaza SOLO el fileId y se
                        // reactiva el escaneo de carátula. Sin duplicados.
                        val repairIdx = result.indexOfFirst { ex ->
                            ex.fileId != a.fileId && (
                                (ex.title.equals(a.title, ignoreCase = true) &&
                                    (ex.artist.equals(a.artist, ignoreCase = true) ||
                                     ex.artist.equals("Desconocido", ignoreCase = true) ||
                                     abs(ex.durationSec - a.duration) <= 2)) ||
                                (a.fileName.isNotBlank() && ex.fileName.isNotBlank() &&
                                        ex.fileName == a.fileName)
                            )
                        }
                        if (repairIdx >= 0) {
                            val old = result[repairIdx]
                            result[repairIdx] = old.copy(
                                fileId = a.fileId,
                                caption = a.caption.ifBlank { old.caption },
                                fileName = a.fileName.ifBlank { old.fileName },
                                metaDone = false
                            )
                            known.add(a.fileId)
                            repaired++
                            continue
                        }

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

                    if (added + imported + repaired > 0) {
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
                if (repaired > 0) sb.append("Reparadas: $reparadas canciones")
                if (imported > 0) {
                    if (sb.isNotEmpty()) sb.append(" · ")
                    sb.append("Importadas: $imported del historial")
                }
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

    private fun extractImportFileId(update: JSONObject): String? {
        val msg = update.optJSONObject("message") ?: return null
        val doc = msg.optJSONObject("document") ?: return null
        val name = doc.optString("file_name", "")
        if (!name.equals("melodix_import.json", ignoreCase = true)) return null
        val fid = doc.optString("file_id", "")
        return if (fid.isBlank()) null else fid
    }

    private fun importFromJson(
        client: TelegramClient,
        fileId: String,
        result: MutableList<Song>,
        known: MutableSet<String>
    ): Pair<Int, String?> {
        return try {
            val url = client.fileUrl(client.getFilePath(fileId))
            val body = URL(url).readText()
            val n = parseAndMerge(body, result, known)
            if (n < 0) 0 to "el file_id no apunta al JSON (vuelve a enviar melodix_import.json al bot)"
            else n to null
        } catch (e: Exception) {
            0 to (e.message ?: "desconocido")
        }
    }

    // Convierte texto JSON en canciones evitando duplicados.
    // Lo usan la importación online y el catálogo semilla.
    private fun parseAndMerge(
        body: String,
        result: MutableList<Song>,
        known: MutableSet<String>
    ): Int {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("[")) return -1
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
                    genre = detectGenre(caption, fileName, null) ?:
