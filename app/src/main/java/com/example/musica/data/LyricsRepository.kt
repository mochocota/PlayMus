package com.example.musica.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// Una línea de letra sincronizada: momento (ms) + texto
data class LyricLine(val timeMs: Long, val text: String)

data class LyricsResult(
    val synced: List<LyricLine>,   // con marcas de tiempo (se sigue cantando)
    val plain: String?             // solo texto (sin sincronía)
)

// Resultado de una traducción: idioma detectado + traducción por línea
data class TranslationResult(val lang: String, val lines: List<String>)

// Busca letras en LRCLIB (https://lrclib.net) y traduce con el
// servicio de traducción de Google (endpoint público, gratis, sin clave).
object LyricsRepository {

    private const val BASE = "https://lrclib.net/api"

    // Caché en memoria: songId -> resultado (null = no encontrada)
    private val cache = ConcurrentHashMap<Int, LyricsResult?>()

    // Caché de traducciones: songId -> resultado (idioma + líneas)
    private val translationCache = ConcurrentHashMap<Int, TranslationResult>()

    suspend fun getFor(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
        if (cache.containsKey(song.id)) return@withContext cache[song.id]
        val result = fetch(song.title, song.artist, song.durationSec)
        cache[song.id] = result
        result
    }

    // ============================================================
    // TRADUCCIÓN BILINGÜE: recibe las líneas en su idioma y
    // devuelve una traducción alineada línea por línea.
    // Técnica: numerar cada línea ("1. ...", "2. ...") para que el
    // traductor no fusione ni reordene, y reconstruir por número.
    // ============================================================
    suspend fun getTranslation(songId: Int, lines: List<String>): TranslationResult? =
        withContext(Dispatchers.IO) {
            translationCache[songId]?.let { return@withContext it }

            val texts = lines.map { it.trim() }
            if (texts.all { it.isEmpty() }) return@withContext null

            // Traducir solo las líneas con texto; las vacías quedan vacías
            val numbered = texts.mapIndexed { i, t ->
                if (t.isEmpty()) null else "${i + 1}. $t"
            }

            // Trocear en peticiones de ~1200 caracteres (límite cómodo)
            val chunks = mutableListOf<List<Int>>()
            var cur = mutableListOf<Int>()
            var curLen = 0
            numbered.forEachIndexed { i, t ->
                if (t != null) {
                    if (curLen + t.length > 1200 && cur.isNotEmpty()) {
                        chunks.add(cur); cur = mutableListOf(); curLen = 0
                    }
                    cur.add(i); curLen += t.length
                }
            }
            if (cur.isNotEmpty()) chunks.add(cur)

            val result = Array(texts.size) { "" }
            var detected = "en"

            for (chunk in chunks) {
                val payload = chunk.joinToString("\n") { i -> numbered[i] ?: "" }
                val (lang, translated) = gtxTranslate(payload) ?: return@withContext null
                detected = lang
                if (lang.startsWith("es")) {
                    // La letra ya está en español: no se necesita traducción
                    return@withContext TranslationResult("es", emptyList())
                }

                // Reconstruir por número de línea
                val map = HashMap<Int, String>()
                val respLines = translated.split("\n")
                for (rl in respLines) {
                    val m = Regex("^\\s*(\\d+)[.)\\-]?\\s*(.+)$").find(rl.trim())
                    if (m != null) {
                        val num = m.groupValues[1].toIntOrNull() ?: continue
                        map[num] = m.groupValues[2].trim()
                    }
                }
                // Si el traductor perdió los números pero el total
                // coincide, mapear en orden
                if (map.size < chunk.size / 2 && respLines.size == chunk.size) {
                    chunk.forEachIndexed { j, i ->
                        val line = respLines.getOrNull(j)?.trim().orEmpty()
                        val m = Regex("^\\s*(\\d+)[.)\\-]?\\s*").find(line)
                        map[i + 1] = if (m != null) m.value.replace(Regex("^\\s*(\\d+)[.)\\-]?\\s*"), "").trim() else line
                    }
                }
                chunk.forEachIndexed { j, i ->
                    result[i] = map[j + 1] ?: ""
                }
            }

            // Si casi todo quedó vacío, la alineación falló
            val filled = result.count { it.isNotBlank() }
            if (filled < texts.count { it.isNotEmpty() } / 2) return@withContext null

            val res = TranslationResult(detected, result.toList())
            translationCache[songId] = res
            res
        }

    // Traduce un bloque de texto libre (para letras sin sincronía)
    suspend fun translateText(text: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        gtxTranslate(text)
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

    // ============================================================
    // Traducción vía Google Translate (endpoint público gtx).
    // Devuelve (idioma detectado, texto traducido) o null si falla.
    // ============================================================
    private fun gtxTranslate(text: String, target: String = "es"): Pair<String, String>? {
        val conn = try {
            val q = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=$target&dt=t&q=$q"
            (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Melodix/1.0")
            }
        } catch (_: Exception) { return null }

        return try {
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            val segs = arr.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segs.length()) {
                sb.append(segs.getJSONArray(i).optString(0, ""))
            }
            val detected = arr.optString(2, "en")
            detected to sb.toString()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
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
