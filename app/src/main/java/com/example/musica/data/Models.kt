package com.example.musica.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// =====================================================================
// DATOS DE EJEMPLO — cambia DEMO_URL por tus propias canciones (MP3)
// =====================================================================
const val DEMO_URL = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int,
    val url: String = DEMO_URL
)

data class PlaylistItem(val id: String, val name: String, val count: Int,
                        val icon: ImageVector, val color: Color)

data class LibItem(val icon: ImageVector, val label: String, val count: String, val route: String? = null)

data class ForYouItem(val id: String, val name: String, val gradient: Brush)

data class TrendItem(val name: String, val subtitle: String, val gradient: Brush)

data class ArtistItem(val name: String, val count: Int, val emoji: String)

data class RecentItem(val title: String, val subtitle: String)

val ff6Songs = listOf(
    Song(1, "Terra's Theme", "Nobuo Uematsu", "Final Fantasy VI", 268),
    Song(2, "Awake", "Nobuo Uematsu", "Final Fantasy VI", 195),
    Song(3, "Dancing Mad", "Nobuo Uematsu", "Final Fantasy VI", 772),
    Song(4, "The Decisive Battle", "Nobuo Uematsu", "Final Fantasy VI", 340),
    Song(5, "Ending Theme", "Nobuo Uematsu", "Final Fantasy VI", 154)
)

val chillSongs = listOf(
    Song(6, "Sunset Lover", "Petit Biscuit", "Chill Vibes", 238),
    Song(7, "Space Song", "Beach House", "Chill Vibes", 320),
    Song(8, "Midnight", "Coldplay", "Chill Vibes", 294),
    Song(9, "The Night We Met", "Lord Huron", "Chill Vibes", 208),
    Song(10, "Good Days", "SZA", "Chill Vibes", 278)
)

val extraSongs = listOf(
    Song(11, "One Winged Angel", "Nobuo Uematsu", "Final Fantasy VII", 388),
    Song(12, "To Zanarkand", "Nobuo Uematsu", "Final Fantasy X", 334)
)

val queueDemo = ff6Songs.drop(1) + extraSongs

val playlists = listOf(
    PlaylistItem("favoritas", "Favoritas", 248, Icons.Filled.Favorite, Color(0xFF8B5CF6)),
    PlaylistItem("rock", "Rock Clásico", 120, Icons.Filled.MusicNote, Color(0xFFF97316)),
    PlaylistItem("rpg", "RPG Soundtracks", 87, Icons.Filled.SportsEsports, Color(0xFF7C3AED)),
    PlaylistItem("chill", "Chill Vibes", 64, Icons.Filled.BeachAccess, Color(0xFF22D3EE)),
    PlaylistItem("gym", "Gym Motivation", 56, Icons.Filled.FitnessCenter, Color(0xFF3B82F6)),
    PlaylistItem("80s", "80s & 90s", 102, Icons.Filled.Radio, Color(0xFFEC4899))
)

val libraryItems = listOf(
    LibItem(Icons.Filled.MusicNote, "Canciones", "1,248"),
    LibItem(Icons.Filled.Album, "Álbumes", "86"),
    LibItem(Icons.Filled.Person, "Artistas", "42", "artists"),
    LibItem(Icons.AutoMirrored.Filled.QueueMusic, "Listas de reproducción", "18", "playlists"),
    LibItem(Icons.Filled.Download, "Descargas", "12")
)

val recents = listOf(
    RecentItem("Final Fantasy VI", "Álbum · Various Artists"),
    RecentItem("The Weeknd", "Artista"),
    RecentItem("Imagine Dragons", "Artista")
)

val recentHome = listOf(
    "Final Fantasy VI" to "Playlist",
    "The Weeknd" to "Artista",
    "Imagine Dragons" to "Artista"
)

val forYou = listOf(
    ForYouItem("rock", "Rock Clásico", Brush.verticalGradient(listOf(Color(0xFF3730A3), Color(0xFF7C3AED)))),
    ForYouItem("80s", "Lo mejor del Pop", Brush.verticalGradient(listOf(Color(0xFF6D28D9), Color(0xFFEC4899)))),
    ForYouItem("chill", "Chill Vibes", Brush.verticalGradient(listOf(Color(0xFF0E7490), Color(0xFF22D3EE))))
)

val genres = listOf("Rock", "Pop", "Hip Hop", "Electrónica")

val trends = listOf(
    TrendItem("Today's Hits", "Playlist", Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899)))),
    TrendItem("Rock Anthems", "Playlist", Brush.verticalGradient(listOf(Color(0xFFB91C1C), Color(0xFF7C3AED)))),
    TrendItem("LoFi Beats", "Playlist", Brush.verticalGradient(listOf(Color(0xFF0F766E), Color(0xFF22D3EE))))
)

val artists = listOf(
    ArtistItem("Imagine Dragons", 12, "🐉"),
    ArtistItem("The Weeknd", 12, "🌃"),
    ArtistItem("Coldplay", 14, "🎹"),
    ArtistItem("Daft Punk", 10, "🤖"),
    ArtistItem("Ariana Grande", 12, "🎤"),
    ArtistItem("Linkin Park", 12, "🔥")
)

fun songsFor(playlistId: String): List<Song> = when (playlistId) {
    "chill" -> chillSongs
    else -> ff6Songs + extraSongs
}

fun fmtSeconds(s: Int) = "%d:%02d".format(s / 60, s % 60)

fun fmtMillis(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
