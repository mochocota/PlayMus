package com.example.musica.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Canciones de demostración (se usan si aún no sincronizas tu canal)
const val DEMO_URL = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int,
    val url: String = DEMO_URL,
    val fileId: String = "",
    val genre: String = "Otros",
    val fromTelegram: Boolean = false,
    val scanned: Boolean = false,
    val coverPath: String = "",
    val metaDone: Boolean = false
)

data class PlaylistItem(val id: String, val name: String, val count: Int,
                        val icon: ImageVector, val color: Color)

data class LibItem(val icon: ImageVector, val label: String, val count: String, val route: String? = null)

data class ForYouItem(val id: String, val name: String, val gradient: Brush)

data class TrendItem(val name: String, val subtitle: String, val gradient: Brush)

data class ArtistItem(val name: String, val count: Int, val emoji: String)

data class RecentItem(val title: String, val subtitle: String)

val ff6Songs = listOf(
    Song(1, "Terra's Theme", "Nobuo Uematsu", "Final Fantasy VI", 268, genre = "Bandas Sonoras"),
    Song(2, "Awake", "Nobuo Uematsu", "Final Fantasy VI", 195, genre = "Bandas Sonoras"),
    Song(3, "Dancing Mad", "Nobuo Uematsu", "Final Fantasy VI", 772, genre = "Bandas Sonoras"),
    Song(4, "The Decisive Battle", "Nobuo Uematsu", "Final Fantasy VI", 340, genre = "Bandas Sonoras"),
    Song(5, "Ending Theme", "Nobuo Uematsu", "Final Fantasy VI", 154, genre = "Bandas Sonoras")
)

val chillSongs = listOf(
    Song(6, "Sunset Lover", "Petit Biscuit", "Chill Vibes", 238, genre = "Electrónica"),
    Song(7, "Space Song", "Beach House", "Chill Vibes", 320, genre = "Electrónica"),
    Song(8, "Midnight", "Coldplay", "Chill Vibes", 294, genre = "Pop"),
    Song(9, "The Night We Met", "Lord Huron", "Chill Vibes", 208, genre = "Folk"),
    Song(10, "Good Days", "SZA", "Chill Vibes", 278, genre = "R&B")
)

val extraSongs = listOf(
    Song(11, "One Winged Angel", "Nobuo Uematsu", "Final Fantasy VII", 388, genre = "Bandas Sonoras"),
    Song(12, "To Zanarkand", "Nobuo Uematsu", "Final Fantasy X", 334, genre = "Bandas Sonoras")
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

// ============================================================
// CATEGORÍAS: mapa de palabras clave → género en español
// (se usa con hashtags del caption y con la etiqueta ID3)
// ============================================================
val genreMap: List<Pair<String, String>> = listOf(
    "pop" to "Pop",
    "rock" to "Rock",
    "ballad" to "Balada", "balada" to "Balada",
    "salsa" to "Salsa",
    "grupera" to "Grupera", "grupero" to "Grupera", "sierreno" to "Grupera",
    "sierreño" to "Grupera", "duranguense" to "Grupera",
    "cumbia" to "Cumbia",
    "banda" to "Banda",
    "reggaeton" to "Reggaetón", "reggeaton" to "Reggaetón", "regueton" to "Reggaetón",
    "electronic" to "Electrónica", "electronica" to "Electrónica", "edm" to "Electrónica",
    "techno" to "Electrónica", "house" to "Electrónica", "dance" to "Electrónica",
    "chill" to "Electrónica",
    "hip hop" to "Hip Hop", "hiphop" to "Hip Hop", "hip-hop" to "Hip Hop",
    "rap" to "Rap",
    "trap" to "Trap",
    "jazz" to "Jazz",
    "classical" to "Clásica", "clasica" to "Clásica", "classique" to "Clásica",
    "vallenato" to "Vallenato",
    "norteno" to "Norteño", "norteño" to "Norteño",
    "ranchera" to "Ranchera", "mariachi" to "Ranchera", "ranchero" to "Ranchera",
    "corrido" to "Corridos", "corridos" to "Corridos", "tumbado" to "Corridos",
    "bachata" to "Bachata",
    "merengue" to "Merengue",
    "disco" to "Disco",
    "folk" to "Folk", "folclor" to "Folk", "folklore" to "Folk",
    "metal" to "Metal",
    "punk" to "Punk",
    "r&b" to "R&B", "rnb" to "R&B", "rhythm and blues" to "R&B",
    "reggae" to "Reggae",
    "gospel" to "Cristiana", "christian" to "Cristiana", "cristiana" to "Cristiana",
    "instrumental" to "Instrumental",
    "bolero" to "Bolero",
    "tropical" to "Tropical",
    "soundtrack" to "Bandas Sonoras", "banda sonora" to "Bandas Sonoras",
    "score" to "Bandas Sonoras", "ost" to "Bandas Sonoras", "soundtracks" to "Bandas Sonoras"
)

fun genreColor(genre: String): Color = when (genre.lowercase()) {
    "pop" -> Color(0xFFEC4899)
    "rock" -> Color(0xFFEF4444)
    "balada" -> Color(0xFF8B5CF6)
    "salsa" -> Color(0xFFF97316)
    "grupera" -> Color(0xFF22C55E)
    "cumbia" -> Color(0xFF14B8A6)
    "banda" -> Color(0xFFEAB308)
    "vallenato" -> Color(0xFF84CC16)
    "norteño", "norteno" -> Color(0xFF16A34A)
    "ranchera" -> Color(0xFFD97706)
    "corridos" -> Color(0xFF65A30D)
    "reggaetón", "reggaeton" -> Color(0xFFA855F7)
    "electrónica", "electronica" -> Color(0xFF22D3EE)
    "hip hop" -> Color(0xFFF43F5E)
    "rap" -> Color(0xFFE11D48)
    "trap" -> Color(0xFF7C3AED)
    "jazz" -> Color(0xFF6366F1)
    "clásica", "clasica" -> Color(0xFF93C5FD)
    "disco" -> Color(0xFFFBBF24)
    "folk" -> Color(0xFFA16207)
    "metal" -> Color(0xFF475569)
    "punk" -> Color(0xFFB91C1C)
    "r&b" -> Color(0xFF818CF8)
    "reggae" -> Color(0xFF10B981)
    "cristiana" -> Color(0xFF60A5FA)
    "instrumental" -> Color(0xFF94A3B8)
    "bolero" -> Color(0xFFFB7185)
    "tropical" -> Color(0xFF06B6D4)
    "bandas sonoras" -> Color(0xFF6D28D9)
    else -> Color(0xFF64748B)
}

fun genreIcon(genre: String): ImageVector = when (genre.lowercase()) {
    "pop" -> Icons.Filled.Star
    "rock" -> Icons.Filled.Whatshot
    "balada" -> Icons.Filled.Favorite
    "salsa" -> Icons.Filled.LocalFireDepartment
    "grupera" -> Icons.Filled.Piano
    "cumbia" -> Icons.Filled.Celebration
    "banda" -> Icons.Filled.MilitaryTech
    "vallenato" -> Icons.Filled.QueueMusic
    "norteño", "norteno" -> Icons.Filled.MusicNote
    "ranchera" -> Icons.Filled.Audiotrack
    "corridos" -> Icons.Filled.MusicVideo
    "reggaetón", "reggaeton" -> Icons.Filled.Nightlife
    "electrónica", "electronica" -> Icons.Filled.Bolt
    "hip hop" -> Icons.Filled.Mic
    "rap" -> Icons.Filled.RecordVoiceOver
    "trap" -> Icons.Filled.Headphones
    "jazz" -> Icons.Filled.Lyrics
    "clásica", "clasica" -> Icons.Filled.Piano
    "disco" -> Icons.Filled.DiscFull
    "folk" -> Icons.Filled.Park
    "metal" -> Icons.Filled.Equalizer
    "punk" -> Icons.Filled.FlashOn
    "r&b" -> Icons.Filled.Radio
    "reggae" -> Icons.Filled.WbSunny
    "cristiana" -> Icons.Filled.Church
    "instrumental" -> Icons.Filled.GraphicEq
    "bolero" -> Icons.Filled.FavoriteBorder
    "tropical" -> Icons.Filled.Waves
    "bandas sonoras" -> Icons.Filled.Movie
    else -> Icons.Filled.MusicNote
}

fun fmtSeconds(s: Int) = "%d:%02d".format(s / 60, s % 60)

fun fmtMillis(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
