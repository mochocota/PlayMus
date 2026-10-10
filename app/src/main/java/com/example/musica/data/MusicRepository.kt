package com.example.musica.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

object MusicRepository {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val mutex = Mutex()
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    // ---------- Consultas ----------
    fun genresWithCount(): List<Pair<String, Int>> =
        _songs.value.groupingBy { it.genre }.eachCount().toList().sortedBy { it.first.lowercase() }

    fun songsByGenre(genre: String): List<Song> =
        if (genre == "Todas") _songs.value else _songs.value.filter { it.genre == genre }

    fun artistCount(): Int = _songs.value.map { it.artist }.distinct().size
    fun albumCount(): Int = _songs.value.map { it.album }.filter { it.isNotBlank() }.distinct().size

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

            val known = _songs.value.map { it.fileId }.toMutableSet()
            val result = _songs.value.toMutableList()
            var added = 0
            var skipped = 0

            for (u in updates) {
                val a = TelegramClient.extractAudio(u) ?: continue
                if (a.fileId in known) continue
                if (a.fileSize > 19_500_000) { skipped++; continue } // límite de bots: 20 MB
                known.add(a.fileId)
                result.add(
                    0, Song(
                        id = a.fileId.hashCode(),
                        title = a.title, artist = a.artist, album = "",
                        durationSec = a.duration, url = "",
                        fileId = a.fileId,
                        genre = genreFromCaption(a.caption),
                        fromTelegram = true
                    )
                )
                added++
            }
            _songs.value = result
            save()

            // Confirmar las actualizaciones como leídas (no se repiten)
            val maxId = updates.maxOfOrNull { it.optLong("update_id", -1L) } ?: -1L
            if (maxId >= 0) {
                TelegramConfig.saveOffset(context, maxId + 1)
                try { client.getUpdates(maxId + 1, timeoutSec = 0) } catch (_: Exception) {}
            }

            // Leer el género real (etiqueta ID3) de algunas canciones sin hashtag
            val detected = scanPendingGenres(client, limit = 8)

            val sb = StringBuilder("Sincronizado: $added canciones nuevas")
            if (skipped > 0) sb.append(" · $skipped omitidas (>20MB)")
            if (detected > 0) sb.append(" · $detected géneros detectados")
            if (updates.isEmpty() && added == 0) sb.append(" (sin cambios)")
            sb.toString()
        }
    }

    // Descarga solo los metadatos (no el archivo completo) para detectar el género ID3
    private suspend fun scanPendingGenres(client: TelegramClient, limit: Int): Int =
        withContext(Dispatchers.IO) {
            var detected = 0
            val pending = _songs.value.filter { it.fromTelegram && !it.scanned }.take(limit)
            val updated = _songs.value.toMutableList()
            for (song in pending) {
                val mapped = try {
                    val url = client.fileUrl(client.getFilePath(song.fileId))
                    val retriever = MediaMetadataRetriever()
                    val g = try {
                        retriever.setDataSource(url)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    } finally {
                        retriever.release()
                    }
                    mappedGenre(g)
                } catch (_: Exception) { null }
                val index = updated.indexOfFirst { it.id == song.id }
                if (index >= 0) {
                    updated[index] = updated[index].copy(genre = mapped ?: "Otros", scanned = true)
                    if (mapped != null) detected++
                }
            }
            if (pending.isNotEmpty()) { _songs.value = updated; save() }
            detected
        }

    // ---------- Géneros ----------
    private fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().trim()

    private val genreNames = listOf(
        "Pop", "Rock", "Balada", "Salsa", "Grupera", "Cumbia", "Banda",
        "Reggaetón", "Electrónica", "Hip Hop", "Jazz", "Clásica",
        "Vallenato", "Norteño", "Ranchera"
    )

    private fun genreFromCaption(caption: String): String {
        val tags = Regex("#\\s*([\\p{L}]+)").findAll(caption).map { norm(it.groupValues[1]) }.toList()
        for (g in genreNames) if (norm(g) in tags) return g
        val after = Regex("g[eé]nero\\s*:\\s*([\\p{L} ]+)").find(caption)?.groupValues?.get(1)?.trim()
        if (after != null) {
            for (g in genreNames) if (norm(g) in norm(after)) return g
            return after.replaceFirstChar { it.uppercase() }
        }
        return "Otros"
    }

    private fun mappedGenre(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val n = norm(raw)
        for (g in genreNames) if (n.contains(norm(g))) return g
        return raw.split("/", "(", "-").first().trim()
            .replaceFirstChar { it.uppercase() }.ifBlank { null }
    }

    // ---------- Almacenamiento local (JSON en el teléfono) ----------
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
                    scanned = o.optBoolean("scanned")
                )
            }
            _songs.value = list
        } catch (_: Exception) {}
    }
}
