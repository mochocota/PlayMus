package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.musica.data.DownloadRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.data.fmtSeconds
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun DownloadsScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songs by MusicRepository.songs.collectAsState()
    val dlVersion by DownloadRepository.version.collectAsState()

    val list = remember(dlVersion, songs) {
        songs.filter { DownloadRepository.isDownloaded(it.id) }
    }
    val totalMb = remember(dlVersion) { DownloadRepository.totalBytes() / (1024 * 1024) }

    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  Descargas", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteAll = true }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, "Borrar todas", tint = Pink)
                }
            }
            Text("${list.size} canciones · ${totalMb} MB en el teléfono",
                color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp))
            Spacer(Modifier.height(10.dp))

            // ---------- Botones: Reproducir todo / Aleatorio ----------
            if (list.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Reproducir todo (en orden)
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GradientBrush)
                            .clickable { vm.play(list.first(), list) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir todo", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // Aleatorio (mezcla; no se repite ninguna)
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardLighter)
                            .clickable {
                                if (vm.repeatOne) vm.toggleRepeat()
                                val mezcla = list.shuffled()
                                vm.play(mezcla.first(), mezcla)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Shuffle, null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aleatorio", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (list.isEmpty()) {
                Text(
                    "No tienes canciones descargadas.\n\nAbre una canción en el reproductor y toca el botón ⬇️ para guardarla en el teléfono y escucharla sin internet.",
                    color = TextSecondary, fontSize = 14.sp
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(list.size) { i ->
                        val s = list[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.play(s, list) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverImage(s, Modifier.size(46.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { songToDelete = s }) {
                                Icon(Icons.Filled.Close, "Eliminar descarga", tint = TextSecondary)
                            }
                            Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }

    songToDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            containerColor = CardDark,
            title = { Text("Eliminar descarga", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar «${s.title}» del teléfono?",
                color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadRepository.delete(s.id)
                    songToDelete = null
                }) { Text("Eliminar", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancelar", color = Purple) }
            }
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            containerColor = CardDark,
            title = { Text("Borrar todas", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar las ${list.size} descargas? Liberarás ${totalMb} MB.",
                color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadRepository.deleteAll()
                    showDeleteAll = false
                }) { Text("Borrar todo", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text("Cancelar", color = Purple) }
            }
        )
    }
}
