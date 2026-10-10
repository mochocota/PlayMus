package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.ff6Songs
import com.example.musica.data.recentHome
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.SectionHeader
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songs by MusicRepository.songs.collectAsState()
    val stats by PlayStatsRepository.counts.collectAsState()
    val library = songs.ifEmpty { ff6Songs }

    // Las 30 canciones más reproducidas (solo cuentan las que tienen reproducciones)
    val topSongs = remember(songs, stats) {
        songs.filter { (stats[it.id] ?: 0) > 0 }
            .sortedByDescending { stats[it.id] ?: 0 }
            .take(30)
    }

    // "Podría gustarte": canciones parecidas (mismo género o mismo artista)
    // a las más reproducidas, sin repetir las del Top. Se rellena con el resto.
    val recommended = remember(songs, stats, topSongs) {
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

        // ---------- Encabezado (sin lupa ni avatar) ----------
        Text("Inicio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text("Buenas emociones,\nSiempre 🎵", color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        // ---------- Banner ----------
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF241A3E), Color(0xFF12305A))))
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hoy es un gran día", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("para escuchar algo épico", color = TextSecondary, fontSize = 13.sp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(GradientBrush)
                .clickable {
                    vm.play(library.first(), library)
                    nav.navigate("player")
                },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
        }

        // ---------- Escuchado recientemente ----------
        Spacer(Modifier.height(22.dp))
        SectionHeader("Escuchado recientemente", "Ver todo")
        Spacer(Modifier.height(12.dp))
        if (songs.isNotEmpty()) {
            val rec = songs.take(6)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rec.size) { i ->
                    val s = rec[i]
                    Column(Modifier.width(110.dp).clickable {
                        vm.play(s, songs)
                        nav.navigate("player")
                    }) {
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentHome.forEach { (name, kind) ->
                    Column(Modifier.width(110.dp)) {
                        AlbumArt(Modifier.size(110.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(kind, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        // ---------- Las 30 más reproducidas ----------
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
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("${i + 1}. ${s.title}", color = Color.White,
                            fontSize = 13.sp, maxLines = 1)
                        Text("▶ ${stats[s.id] ?: 0}", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        // ---------- Podría gustarte ----------
        Spacer(Modifier.height(22.dp))
        Text("Podría gustarte", color = Color.White, fontSize = 16.sp,
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
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp)
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
