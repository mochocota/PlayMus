package com.example.musica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

// Estadísticas de reproducción: cuántas veces sonó cada canción
// y cuándo fue la última vez (para "Escuchado recientemente").
// Persiste al cerrar la app.
object PlayStatsRepository {

    private val _counts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val counts: StateFlow<Map<Int, Int>> = _counts

    private val _lastPlayed = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val lastPlayed: StateFlow<Map<Int, Long>> = _lastPlayed

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun increment(songId: Int) {
        val now = System.currentTimeMillis()
        val cur = _counts.value
        _counts.value = cur + (songId to (cur[songId] ?: 0) + 1)
        _lastPlayed.value = _lastPlayed.value + (songId to now)
        save()
    }

    fun playCount(songId: Int): Int = _counts.value[songId] ?: 0

    // ---------- Almacenamiento local (JSON) ----------
    private fun file() = File(context.filesDir, "play_stats.json")

    private fun save() {
        try {
            val o = JSONObject()
            _counts.value.forEach { (k, v) -> o.put("c_$k", v) }
            _lastPlayed.value.forEach { (k, v) -> o.put("l_$k", v) }
            file().writeText(o.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val counts = mutableMapOf<Int, Int>()
            val last = mutableMapOf<Int, Long>()
            o.keys().forEach { k ->
                when {
                    k.startsWith("c_") -> counts[k.removePrefix("c_").toInt()] = o.optInt(k, 0)
                    k.startsWith("l_") -> last[k.removePrefix("l_").toInt()] = o.optLong(k, 0L)
                }
            }
            _counts.value = counts
            _lastPlayed.value = last
        } catch (_: Exception) {}
    }
}
