package com.example.musica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UserPlaylist(
    val id: String,
    val name: String,
    val songIds: List<Int> = emptyList()
)

object PlaylistRepository {

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        load()
    }

    fun create(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return ""
        val id = "u" + System.currentTimeMillis().toString()
        _playlists.value = _playlists.value + UserPlaylist(id, clean)
        save()
        return id
    }

    fun delete(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        save()
    }

    // Devuelve false si la canción ya estaba (no se duplica)
    fun addSong(playlistId: String, songId: Int): Boolean {
        val pl = _playlists.value.firstOrNull { it.id == playlistId } ?: return false
        if (songId in pl.songIds) return false
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds + songId) else it
        }
        save()
        return true
    }

    fun removeSong(playlistId: String, songId: Int) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        save()
    }

    fun songsIn(playlistId: String, allSongs: List<Song>): List<Song> {
        val pl = _playlists.value.firstOrNull { it.id == playlistId } ?: return emptyList()
        return pl.songIds.mapNotNull { sid -> allSongs.firstOrNull { it.id == sid } }
    }

    // ---------- Almacenamiento local (JSON) ----------
    private fun file() = File(context.filesDir, "user_playlists.json")

    private fun save() {
        try {
            val arr = JSONArray()
            _playlists.value.forEach { p ->
                val ids = JSONArray()
                p.songIds.forEach { ids.put(it) }
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("songIds", ids)
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
                val ids = o.optJSONArray("songIds")
                val songIds = if (ids != null) (0 until ids.length()).map { ids.optInt(it) } else emptyList()
                UserPlaylist(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    songIds = songIds
                )
            }
            _playlists.value = list
        } catch (_: Exception) {}
    }
}
