package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.data.Song
import com.example.musica.data.fmtSeconds
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun UserPlaylistScreen(nav: NavHostController, vm: PlayerViewModel, playlistId: String) {
    val allSongs by MusicRepository.songs.collectAsState()
    val lists by PlaylistRepository.playlists.collectAsState()
    val pl = lists.firstOrNull { it.id == playlistId }
    var songToRemove by remember { mutableStateOf<Song?>(null) }

    if (pl == null) {
        // La lista fue borrada: volver atrás
        LaunchedEffect(Unit) { nav.popBackStack() }
        Box(Modifier.fillMaxSize().background(NavyBg))
        return
    }

    val songs = PlaylistRepository.songsIn(playlistId, allSongs)

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  ${pl.name}", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = {
                    PlaylistRepository.delete(pl.id)
                    nav.popBackStack()
                }) {
                    Icon(Icons.Filled.Delete, "Borrar lista", tint = Pink)
                }
            }
            Text("${songs.size} canciones", color = TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp))
            Spacer(Modifier.height(10.dp))

            if (songs.isEmpty()) {
                Text(
                    "Esta lista está vacía.\n\nAñade canciones con el botón + que aparece en cada canción (en Géneros o en Búsqueda).",
                    color = TextSecondary, fontSize = 14.sp
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(songs.size) { i ->
                        val s = songs[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.play(s, songs) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverImage(s, Modifier.size(46.dp), corner = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { songToRemove = s }) {
                                Icon(Icons.Filled.Close, "Quitar de la lista", tint = TextSecondary)
                            }
                            Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }

    // Confirmación para quitar una canción
    songToRemove?.let { s ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            containerColor = CardDark,
            title = { Text("Quitar de la lista", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Quitar «${s.title}» de «${pl.name}»?",
                    color = TextSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    PlaylistRepository.removeSong(playlistId, s.id)
                    songToRemove = null
                }) { Text("Quitar", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("Cancelar", color = Purple) }
            }
        )
    }
}
