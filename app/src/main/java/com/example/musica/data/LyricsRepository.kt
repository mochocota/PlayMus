package com.example.musica.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

// Una línea de letra sincronizada: momento (ms) + texto
data class LyricLine(val timeMs: Long, val text: String)

data class LyricsResult(
    val synced: List<LyricLine>,   // con marcas de tiempo (se sigue cantando)
    val plain: String?             // solo texto (sin sincronía)
)

// Busca letras en LRCLIB (https://lrclib.net) — gratis, sin registro
object LyricsRepository {

    private const val BASE = "https://lrclib.net/api"

    // Caché en memoria: songId -> resultado (null = no encontrada, no reintenta)
    private val cache = HashMap<Int, LyricsResult?>()

    suspend fun getFor(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
        if (cache.containsKey(song.id)) return@withContext cache[song.id]
        val result = fetch(song.title, song.artist, song.durationSec)
        cache[song.id] = result
        result
    }

    // Limpia el título para la búsqueda: quita "(Video Oficial)", "[Lyrics]", "ft. X"…
    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("\\[.*?\\]"), " ")
        .replace(Regex("\\b(ft|feat|featuring)\\..*$", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun fetch(title: String, artist: String, durationSec: Int): LyricsResult? {
        val t = cleanTitle(title)
        val a = if (artist.isBlank() || artist == "Desconocido") "" else artist

        // 1) Búsqueda exacta (mejor precisión para sincronizar)
        if (a.isNotBlank() && t.isNotBlank()) {
            val q = buildString {
                append("artist_name=").append(enc(a))
                append("&track_name=").append(enc(t))
                if (durationSec > 0) append("&duration=").append(durationSec)
            }
            request("$BASE/get?$q")?.let { toResult(it)?.let { r -> return r } }
        }

        // 2) Búsqueda libre: elige el mejor resultado
        //    (prefiere sincronizada y con duración parecida a la canción)
        if (t.isNotBlank()) {
            val q = enc(listOf(a, t).filter { it.isNotBlank() }.joinToString(" "))
            val arr = requestArray("$BASE/search?q=$q") ?: return null
            val n = minOf(arr.length(), 6)
            var best: JSONObject? = null
            var bestScore = Long.MAX_VALUE
            for (i in 0 until n) {
                val o = arr.getJSONObject(i)
                var score = if (o.optString("syncedLyrics", "").isNotBlank()) 0L else 500_000L
                if (durationSec > 0) score += abs(o.optLong("duration", 0L) - durationSec)
                if (score < bestScore) { bestScore = score; best = o }
            }
            best?.let { toResult(it)?.let { r -> return r } }
        }
        return null
    }

    private fun toResult(o: JSONObject): LyricsResult? {
        val syncedStr = o.optString("syncedLyrics", "").trim()
        val plainStr = o.optString("plainLyrics", "").trim()
        val synced = if (syncedStr.isNotEmpty()) parseLrc(syncedStr) else emptyList()
        if (synced.isEmpty() && plainStr.isEmpty()) return null
        return LyricsResult(synced, plainStr.ifEmpty { null })
    }

    // Convierte el formato LRC "[01:23.45] texto" en líneas con tiempo
    private fun parseLrc(lrc: String): List<LyricLine> {
        val ts = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]")
        val lines = mutableListOf<LyricLine>()
        lrc.lines().forEach { raw ->
            val matches = ts.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val text = ts.replace(raw, "").trim()
            matches.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                lines.add(LyricLine(min * 60000 + sec * 1000 + frac, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(urlStr: String): JSONObject? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Musica/1.0 (Android)")
            if (conn.responseCode !in 200..299) null
            else JSONObject(conn.inputStream.bufferedReader().readText())
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    private fun requestArray(urlStr: String): JSONArray? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Musica/1.0 (Android)")
            if (conn.responseCode !in 200..299) null
            else JSONArray(conn.inputStream.bufferedReader().readText())
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }
}
