package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.HistoryRepository
import com.example.musica.data.MusicRepository
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(nav: NavHostController, vm: PlayerViewModel) {
    val entries by HistoryRepository.entries.collectAsState()
    val songs by MusicRepository.songs.collectAsState()

    val list = entries.mapNotNull { e ->
        songs.firstOrNull { it.id == e.songId }?.let { it to e.at }
    }
    val fmt = SimpleDateFormat("d MMM · HH:mm", Locale("es"))

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  Historial", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${list.size}", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            if (list.isEmpty()) {
                Text("Aún no hay historial.\n\nLas canciones que reproduzcas aparecerán aquí con su fecha.",
                    color = TextSecondary, fontSize = 14.sp)
            } else {
                LazyColumn {
                    items(list.size) { i ->
                        val (s, at) = list[i]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.play(s, list.map { it.first })
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverImage(s, Modifier.size(46.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(fmt.format(Date(at)), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }
}
