package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

// type = "top" | "month" | "week"
@Composable
fun SmartListScreen(nav: NavHostController, vm: PlayerViewModel, type: String) {
    val songs by MusicRepository.songs.collectAsState()
    val stats by PlayStatsRepository.counts.collectAsState()
    val lastPlayed by PlayStatsRepository.lastPlayed.collectAsState()

    val title: String
    val list: List<Song> = when (type) {
        "top" -> {
            title = "Mi Top 50"
            songs.filter { (stats[it.id] ?: 0) > 0 }
                .sortedByDescending { stats[it.id] ?: 0 }
                .take(50)
        }
        "month" -> {
            title = "Lo más escuchado del mes"
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            lastPlayed.filter { it.value >= cutoff }
                .keys.mapNotNull { id -> songs.firstOrNull { it.id == id } }
                .sortedByDescending { stats[it.id] ?: 0 }
                .take(50)
        }
        else -> {
            title = "Lo más escuchado de la semana"
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            lastPlayed.filter { it.value >= cutoff }
                .keys.mapNotNull { id -> songs.firstOrNull { it.id == id } }
                .sortedByDescending { stats[it.id] ?: 0 }
                .take(50)
        }
    }

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  $title", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            }
            Text("${list.size} canciones", color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp))
            Spacer(Modifier.height(10.dp))

            if (list.isNotEmpty()) {
                // Reproducir todo / Aleatorio
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GradientBrush)
                        .clickable { vm.play(list.first(), list) },
                        contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir todo", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(Modifier.weight(1f).height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardLighter)
                        .clickable {
                            if (vm.repeatOne) vm.toggleRepeat()
                            val mezcla = list.shuffled()
                            vm.play(mezcla.first(), mezcla)
                        },
                        contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Shuffle, null, tint = Color.White,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aleatorio", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn {
                    items(list.size) { i ->
                        val s = list[i]
                        Row(Modifier.fillMaxWidth().clickable { vm.play(s, list) }
                            .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = TextSecondary, fontSize = 13.sp,
                                modifier = Modifier.width(26.dp))
                            CoverImage(s, Modifier.size(46.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text("▶ ${stats[s.id] ?: 0}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Text("Aún no hay reproducciones para esta lista.\n\nReproduce canciones y aparecerán aquí automáticamente.",
                    color = TextSecondary, fontSize = 14.sp)
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }
}
