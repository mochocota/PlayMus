package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.FavoritesRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.data.Song
import com.example.musica.data.UserPlaylist
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun PlaylistsScreen(nav: NavHostController) {
    val userLists by PlaylistRepository.playlists.collectAsState()
    val songs by MusicRepository.songs.collectAsState()
    val favIds by FavoritesRepository.ids.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val favSongs = favIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Listas de reproducción", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, "Crear lista", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Favoritos y tus listas", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))

        // Tarjeta de Favoritos + tarjetas de tus listas (2 columnas)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ---------- Favoritos (siempre primero) ----------
            item {
                PlaylistCard(
                    name = "Favoritos",
                    count = favSongs.size,
                    covers = favSongs.take(4),
                    onClick = { nav.navigate("userplaylist/favoritos") },
                    badge = {
                        Icon(Icons.Filled.Favorite, "Favoritos",
                            tint = Pink, modifier = Modifier.size(14.dp))
                    }
                )
            }
            // ---------- Tus listas ----------
            items(userLists.size) { i ->
                val p = userLists[i]
                val covers = PlaylistRepository.songsIn(p.id, songs).take(4)
                PlaylistCard(
                    name = p.name,
                    count = p.songIds.size,
                    covers = covers,
                    onClick = { nav.navigate("userplaylist/${p.id}") },
                    onDelete = { PlaylistRepository.delete(p.id) }
                )
            }
        }
    }

    // ---------- Diálogo: crear lista nueva ----------
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = CardDark,
            title = { Text("Nueva lista", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Nombre de la lista", color = TextSecondary, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = Color.White, focusedBorderColor = Purple,
                        unfocusedBorderColor = TextSecondary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) PlaylistRepository.create(newName)
                    newName = ""
                    showCreate = false
                }) { Text("Crear", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; newName = "" }) {
                    Text("Cancelar", color = Purple)
                }
            }
        )
    }
}

// ---------- Tarjeta cuadrada con collage de 4 portadas ----------
@Composable
private fun PlaylistCard(
    name: String,
    count: Int,
    covers: List<Song>,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Collage 2x2 (rellena con degradado si faltan portadas)
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
                Row(Modifier.weight(1f)) {
                    Cell(covers.getOrNull(0), Modifier.weight(1f).fillMaxHeight())
                    Cell(covers.getOrNull(1), Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f)) {
                    Cell(covers.getOrNull(2), Modifier.weight(1f).fillMaxHeight())
                    Cell(covers.getOrNull(3), Modifier.weight(1f).fillMaxHeight())
                }
            }
            // Insignia (corazón en Favoritos)
            if (badge != null) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp)
                    .size(26.dp).clip(CircleShape).background(Pink.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center) { badge() }
            }
            // Botón borrar (solo listas propias)
            if (onDelete != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .size(28.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, "Borrar lista",
                        tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            // Si no hay canciones aún: icono centrado tenue
            if (covers.isEmpty()) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).size(34.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("$count canciones", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun Cell(song: Song?, modifier: Modifier) {
    if (song != null) {
        CoverImage(song, modifier, corner = 0.dp, sample = 4)
    } else {
        AlbumArt(modifier, corner = 0.dp)
    }
}
