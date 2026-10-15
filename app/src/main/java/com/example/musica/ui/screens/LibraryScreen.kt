package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.FavoritesRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun LibraryScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRaw by MusicRepository.songs.collectAsState()
    val offline by DownloadRepository.offlineMode.collectAsState()
    val songs = if (offline) songsRaw.filter { DownloadRepository.isDownloaded(it.id) }
    else songsRaw
    val userLists by PlaylistRepository.playlists.collectAsState()
    val ready by MusicRepository.ready.collectAsState()
    val loadProgress by MusicRepository.loadProgress.collectAsState()
    val stats by PlayStatsRepository.counts.collectAsState()
    val lastPlayed by PlayStatsRepository.lastPlayed.collectAsState()
    val favIds by FavoritesRepository.ids.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // ---------- Conteos de las listas inteligentes ----------
    val favCount = favIds.count { id -> songs.any { it.id == id } }
    val dlCount = DownloadRepository.downloadedCount()
    val topCount = songs.count { (stats[it.id] ?: 0) > 0 }
    val now = System.currentTimeMillis()
    val songIds = songs.map { it.id }.toSet()
    val monthCount = lastPlayed.count { (key, at) ->
        now - at <= 30L * 24 * 60 * 60 * 1000 && key in songIds
    }
    val weekCount = lastPlayed.count { (key, at) ->
        now - at <= 7L * 24 * 60 * 60 * 1000 && key in songIds
    }

    Column(Modifier.fillMaxSize().background(NavyBg)) {

        // ---------- Encabezado (sin búsqueda) ----------
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Biblioteca", color = Color.White, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Settings, null, tint = Color.White,
                modifier = Modifier
                    .clickable { nav.navigate("settings") }
                    .padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))

        if (!ready) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
        } else {

            if (loadProgress < 1f) {
                Text("📚 Cargando biblioteca… ${songs.size} canciones",
                    color = Cyan, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
            }

            // ---------- Cuadrícula (2 columnas, estilo YouTube Music) ----------
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp
                )
            ) {

                // ---------- Tus "me gusta" ----------
                item {
                    SmartTile(
                        icon = Icons.Filled.FavoriteBorder,
                        title = "Tus \"me gusta\"",
                        subtitle = "$favCount canciones",
                        onClick = { nav.navigate("userplaylist/favoritos") }
                    )
                }

                // ---------- Descargados ----------
                item {
                    SmartTile(
                        icon = Icons.Filled.Download,
                        title = "Descargados",
                        subtitle = "$dlCount canciones",
                        onClick = { nav.navigate("downloads") }
                    )
                }

                // ---------- Mi Top 50 ----------
                item {
                    SmartTile(
                        icon = Icons.Filled.TrendingUp,
                        title = "Mi Top 50",
                        subtitle = "$topCount canciones",
                        onClick = { nav.navigate("smart/top") }
                    )
                }

                // ---------- Lo más escuchado del mes ----------
                item {
                    SmartTile(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Lo más escuchado del mes",
                        subtitle = "$monthCount canciones",
                        onClick = { nav.navigate("smart/month") }
                    )
                }

                // ---------- Lo más escuchado de la semana ----------
                item {
                    SmartTile(
                        icon = Icons.Filled.DateRange,
                        title = "Lo más escuchado de la semana",
                        subtitle = "$weekCount canciones",
                        onClick = { nav.navigate("smart/week") }
                    )
                }

                // ---------- Tus listas (con collage) ----------
                items(userLists.size) { i ->
                    val p = userLists[i]
                    val covers = remember(p.id, songs) {
                        PlaylistRepository.songsIn(p.id, songs).take(4)
                    }
                    Column(Modifier.clickable {
                        nav.navigate("userplaylist/${p.id}")
                    }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardDark)) {
                            if (covers.isEmpty()) {
                                Box(Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center) {
                                    Text("🎵", fontSize = 34.sp)
                                }
                            } else {
                                Collage(covers = covers)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${p.songIds.size} canciones",
                            color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // ---------- Botón + flotante ----------
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = { showCreate = true },
            containerColor = Purple,
            contentColor = Color.White,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, "Crear lista")
        }
    }

    // ---------- Diálogo: crear lista ----------
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
                }) { Text("Crear", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; newName = "" }) {
                    Text("Cancelar", color = Purple)
                }
            }
        )
    }
}

// ---------- Tarjeta inteligente: ícono grande sobre fondo oscuro ----------
@Composable
private fun SmartTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, color = TextSecondary, fontSize = 12.sp)
    }
}

// ---------- Collage 2x2 de portadas ----------
@Composable
private fun Collage(covers: List<Song>) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            CoverImage(covers.getOrNull(0),
                Modifier.weight(1f).fillMaxHeight(), corner = 0.dp, sample = 4)
            CoverImage(covers.getOrNull(1),
                Modifier.weight(1f).fillMaxHeight(), corner = 0.dp, sample = 4)
        }
        Row(Modifier.weight(1f)) {
            CoverImage(covers.getOrNull(2),
                Modifier.weight(1f).fillMaxHeight(), corner = 0.dp, sample = 4)
            CoverImage(covers.getOrNull(3),
                Modifier.weight(1f).fillMaxHeight(), corner = 0.dp, sample = 4)
        }
    }
}
