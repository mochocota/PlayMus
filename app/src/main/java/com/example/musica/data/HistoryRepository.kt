package com.example.musica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

// Historial real de reproducciones (últimas 50), persistente.
object HistoryRepository {

    data class Entry(val songId: Int, val at: Long)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun add(songId: Int) {
        val list = mutableListOf(Entry(songId, System.currentTimeMillis()))
        list.addAll(_entries.value.filter { it.songId != songId })
        _entries.value = list.take(50)
        save()
    }

    private fun file() = File(context.filesDir, "history.json")

    private fun save() {
        try {
            val arr = JSONArray()
            _entries.value.forEach { e ->
                arr.put(JSONObject().put("songId", e.songId).put("at", e.at))
            }
            file().writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            _entries.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.optInt("songId"), o.optLong("at", 0L))
            }
        } catch (_: Exception) {}
    }
}
