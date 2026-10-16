package com.example.musica.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.data.genreColor
import com.example.musica.data.genreIcon
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.components.SongRow
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun GenresScreen(nav: NavHostController) {
    val songs by MusicRepository.songs.collectAsState()
    // Contar y ordenar géneros solo cuando cambia la biblioteca
    // (antes se repetía en cada recomposición de la pantalla)
    val genres = remember(songs) {
        songs.groupingBy { it.genre }.eachCount().toList().sortedBy { it.first.lowercase() }
    }

    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Géneros", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (songs.isEmpty()) {
            Text("Tu biblioteca está vacía.\n\nConecta tu canal de Telegram y pulsa Sincronizar en la Biblioteca.",
                color = TextSecondary, fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    GenreRow("Todas", songs.size, Icons.AutoMirrored.Filled.QueueMusic, Purple) {
                        nav.navigate("genre/" + Uri.encode("Todas"))
                    }
                }
                items(genres.size) { i ->
                    val (name, count) = genres[i]
                    GenreRow(name, count, genreIcon(name), genreColor(name)) {
                        nav.navigate("genre/" + Uri.encode(name))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreRow(name: String, count: Int, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.width(14.dp))
        Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("$count canciones", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
    }
}

@Composable
fun SongListScreen(nav: NavHostController, vm: PlayerViewModel, genre: String) {
    val songs by MusicRepository.songs.collectAsState()
    // Filtrar solo cuando cambian la biblioteca o el género
    val list = remember(songs, genre) {
        if (genre == "Todas") songs else songs.filter { it.genre == genre }
    }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  $genre", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${list.size}", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Text("No hay canciones aquí todavía.", color = TextSecondary, fontSize = 14.sp)
            } else {
                LazyColumn {
                    items(list.size) { i ->
                        val s = list[i]
                        SongRow(song = s, list = list, vm = vm, onAdd = { songToAdd = s })
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }

    AddToPlaylistDialog(songToAdd) { songToAdd = null }
}
