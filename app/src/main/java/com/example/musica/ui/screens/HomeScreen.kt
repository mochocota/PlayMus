package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRaw by MusicRepository.songs.collectAsState()
    val offline by DownloadRepository.offlineMode.collectAsState()
    val songs = if (offline) songsRaw.filter { DownloadRepository.isDownloaded(it.id) }
    else songsRaw

    val stats by PlayStatsRepository.counts.collectAsState()
    val lastPlayed by PlayStatsRepository.lastPlayed.collectAsState()

    // Escuchado recientemente (hasta 15)
    val recentSongs = remember(songs, lastPlayed) {
        lastPlayed.entries.sortedByDescending { it.value }
            .mapNotNull { e -> songs.firstOrNull { it.id == e.key } }
            .take(15)
    }

    // Las más reproducidas (hasta 15)
    val topSongs = remember(songs, stats) {
        songs.filter { (stats[it.id] ?: 0) > 0 }
            .sortedByDescending { stats[it.id] ?: 0 }
            .take(15)
    }

    // Te podría gustar (hasta 15)
    val recommended = remember(songs, topSongs) {
        if (topSongs.isEmpty()) emptyList()
        else {
            val topIds = topSongs.map { it.id }.toSet()
            val pool = songs.filter { it.id !in topIds }
            val topGenres = topSongs.map { it.genre }.toSet()
            val topArtists = topSongs.map { it.artist }.toSet()
            val similar = pool.filter { it.genre in topGenres || it.artist in topArtists }.shuffled()
            val similarIds = similar.map { it.id }.toSet()
            val others = pool.filter { it.id !in similarIds }.shuffled()
            (similar + others).take(15)
        }
    }

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {

        Text("Inicio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ---------- Banner (mismo estilo que Explorar) ----------
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF5B21B6), Color(0xFFBE185D))))
            .padding(18.dp)) {
            Text("Dale play a tu día 😃", color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // ---------- Escuchado recientemente ----------
        Spacer(Modifier.height(22.dp))
        Text("Escuchado recientemente", color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (recentSongs.isEmpty()) {
            Text("Reproduce canciones y aparecerán aquí.",
                color = TextSecondary, fontSize = 13.sp)
        } else {
            Grid3(recentSongs) { s, list ->
                vm.play(s, list)
                nav.navigate("player")
            }
        }

        // ---------- Las más reproducidas ----------
        Spacer(Modifier.height(22.dp))
        Text("Las más reproducidas", color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (topSongs.isEmpty()) {
            Text("Reproduce canciones y aquí aparecerán tus más escuchadas.",
                color = TextSecondary, fontSize = 13.sp)
        } else {
            Grid3(topSongs) { s, list ->
                vm.play(s, list)
                nav.navigate("player")
            }
        }

        // ---------- Te podría gustar ----------
        Spacer(Modifier.height(22.dp))
        Text("Te podría gustar", color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (recommended.isEmpty()) {
            Text("Cuando reproduzcas más canciones te sugeriremos música parecida.",
                color = TextSecondary, fontSize = 13.sp)
        } else {
            Grid3(recommended) { s, list ->
                vm.play(s, list)
                nav.navigate("player")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ============================================================
// Cuadrícula de 3 columnas (filas de 3 dentro del scroll;
// cada sección muestra hasta 15 resultados)
// ============================================================
@Composable
private fun Grid3(list: List<Song>, onPlay: (Song, List<Song>) -> Unit) {
    list.chunked(3).forEach { rowSongs ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rowSongs.forEach { s ->
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onPlay(s, list) }
                ) {
                    CoverImage(s, Modifier.fillMaxWidth().aspectRatio(1f),
                        corner = 12.dp, offlineBadge = true)
                    Spacer(Modifier.height(6.dp))
                    Text(s.title, color = Color.White, fontSize = 12.sp,
                        maxLines = 1, lineHeight = 14.sp)
                    Text(s.artist, color = TextSecondary, fontSize = 10.sp,
                        maxLines = 1, lineHeight = 12.sp)
                }
            }
            // Relleno para alinear filas incompletas
            repeat(3 - rowSongs.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
