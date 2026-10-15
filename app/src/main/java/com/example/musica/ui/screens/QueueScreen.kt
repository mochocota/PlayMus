package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.Song
import com.example.musica.data.fmtSeconds
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun QueueScreen(nav: NavHostController, vm: PlayerViewModel) {
    val queue = vm.queue
    val pos = vm.queuePosition

    // ---------- Añadir a lista ----------
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  En cola", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${queue.size} canciones", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            if (queue.isEmpty()) {
                Text("No hay cola activa.\n\nReproduce una canción y aquí verás la lista que está sonando.",
                    color = TextSecondary, fontSize = 14.sp)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {

                    // ---------- SONANDO AHORA ----------
                    Text("Sonando ahora", color = TextSecondary, fontSize = 12.sp,
                        letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    val now = queue.getOrNull(pos)
                    if (now != null) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardLighter)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverImage(now, Modifier.size(48.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(now.title, color = Cyan, fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(now.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Icon(Icons.Filled.MusicNote, null, tint = Cyan)
                        }
                    }

                    // ---------- A CONTINUACIÓN ----------
                    val upcoming = queue.drop(pos + 1)
                    if (upcoming.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("A continuación (${upcoming.size})", color = TextSecondary,
                            fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        upcoming.forEachIndexed { i, s ->
                            val realIndex = pos + 1 + i
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.jumpTo(realIndex) }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}", color = TextSecondary, fontSize = 13.sp,
                                    modifier = Modifier.width(24.dp))
                                CoverImage(s, Modifier.size(44.dp), corner = 10.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                    Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                                // Añadir a lista
                                IconButton(onClick = { songToAdd = s }) {
                                    Icon(Icons.Filled.PlaylistAdd, "Añadir a lista",
                                        tint = TextSecondary)
                                }
                                Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // ---------- ANTERIORES ----------
                    val previous = queue.take(pos)
                    if (previous.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Anteriores (${previous.size})", color = TextSecondary,
                            fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        previous.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.jumpTo(i) }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}", color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 13.sp, modifier = Modifier.width(24.dp))
                                CoverImage(s, Modifier.size(44.dp), corner = 10.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
                                    Text(s.artist, color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 12.sp, maxLines = 1)
                                }
                                // Añadir a lista
                                IconButton(onClick = { songToAdd = s }) {
                                    Icon(Icons.Filled.PlaylistAdd, "Añadir a lista",
                                        tint = TextSecondary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }

    // ---------- Diálogo: añadir a lista (o crear una nueva) ----------
    AddToPlaylistDialog(songToAdd) { songToAdd = null }
}
