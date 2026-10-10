package com.example.musica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

// Guarda cuántas veces se ha reproducido cada canción (persiste al cerrar la app)
object PlayStatsRepository {

    private val _counts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val counts: StateFlow<Map<Int, Int>> = _counts

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun increment(songId: Int) {
        val cur = _counts.value
        _counts.value = cur + (songId to (cur[songId] ?: 0) + 1)
        save()
    }

    fun playCount(songId: Int): Int = _counts.value[songId] ?: 0

    // ---------- Almacenamiento local (JSON) ----------
    private fun file() = File(context.filesDir, "play_stats.json")

    private fun save() {
        try {
            val o = JSONObject()
            _counts.value.forEach { (k, v) -> o.put(k.toString(), v) }
            file().writeText(o.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val map = mutableMapOf<Int, Int>()
            o.keys().forEach { k -> map[k.toInt()] = o.optInt(k, 0) }
            _counts.value = map
        } catch (_: Exception) {}
    }
}
