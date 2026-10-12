package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRaw by MusicRepository.songs.collectAsState()
    val offline by DownloadRepository.offlineMode.collectAsState()
    // En modo offline: solo las descargadas
    val songs = if (offline) songsRaw.filter { DownloadRepository.isDownloaded(it.id) }
    else songsRaw

    val stats by PlayStatsRepository.counts.collectAsState()
    val lastPlayed by PlayStatsRepository.lastPlayed.collectAsState()

    // Escuchado recientemente: historial REAL por fecha de reproducción
    val recentSongs = remember(songs, lastPlayed) {
        lastPlayed.entries.sortedByDescending { it.value }
            .mapNotNull { e -> songs.firstOrNull { it.id == e.key } }
            .take(12)
    }

    // Las más reproducidas (hasta 30)
    val topSongs = remember(songs, stats) {
        songs.filter { (stats[it.id] ?: 0) > 0 }
            .sortedByDescending { stats[it.id] ?: 0 }
            .take(30)
    }

    // Te podría gustar: parecidas (género o artista) a las más reproducidas
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
            (similar + others).take(30)
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

        // Aviso de modo offline
        if (offline) {
            Spacer(Modifier.height(12.dp))
            Text("✈️ Modo offline · mostrando tus ${songs.size} descargas",
                color = Cyan, fontSize = 12.sp)
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentSongs.size) { i ->
                    val s = recentSongs[i]
                    Column(Modifier.width(110.dp).clickable {
                        vm.play(s, recentSongs)
                        nav.navigate("player")
                    }) {
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp, offlineBadge = true)
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // ---------- Las más reproducidas ----------
        Spacer(Modifier.height(22.dp))
        Text("Las más reproducidas", color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (topSongs.isEmpty()) {
            Text("Reproduce canciones y aquí aparecerán tus 30 más escuchadas.",
                color = TextSecondary, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(topSongs.size) { i ->
                    val s = topSongs[i]
                    Column(Modifier.width(110.dp).clickable {
                        vm.play(s, topSongs)
                        nav.navigate("player")
                    }) {
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp, offlineBadge = true)
                        Spacer(Modifier.height(8.dp))
                        Text("${i + 1}. ${s.title}", color = Color.White,
                            fontSize = 13.sp, maxLines = 1)
                        Text("▶ ${stats[s.id] ?: 0}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recommended.size) { i ->
                    val s = recommended[i]
                    Column(Modifier.width(110.dp).clickable {
                        vm.play(s, recommended)
                        nav.navigate("player")
                    }) {
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp, offlineBadge = true)
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
