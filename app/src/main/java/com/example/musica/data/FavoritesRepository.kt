package com.example.musica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

// Guarda los IDs de las canciones marcadas con corazón (persiste al cerrar la app)
object FavoritesRepository {

    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun isFavorite(songId: Int): Boolean = songId in _ids.value

    // Cambia el estado y devuelve el nuevo (true = ahora es favorita)
    fun toggle(songId: Int): Boolean {
        val nuevo = if (songId in _ids.value) _ids.value - songId else _ids.value + songId
        _ids.value = nuevo
        save()
        return songId in nuevo
    }

    fun remove(songId: Int) {
        _ids.value = _ids.value - songId
        save()
    }

    // ---------- Almacenamiento local ----------
    private fun file() = File(context.filesDir, "favorites.json")

    private fun save() {
        try {
            val arr = JSONArray()
            _ids.value.forEach { arr.put(it) }
            file().writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            _ids.value = (0 until arr.length()).map { arr.optInt(it) }.toSet()
        } catch (_: Exception) {}
    }
}
