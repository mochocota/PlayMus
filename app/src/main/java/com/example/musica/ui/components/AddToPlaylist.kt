package com.example.musica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.data.Song
import com.example.musica.data.fmtSeconds
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

// Fila de canción reutilizable: tocar reproduce, 🏷️ cambia género, + añade a lista
@Composable
fun SongRow(song: Song, list: List<Song>, vm: PlayerViewModel, onAdd: () -> Unit) {
    var showGenre by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { vm.play(song, list) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(song, Modifier.size(46.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
            Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = { showGenre = true }) {
            Icon(Icons.Filled.Category, "Cambiar género", tint = Cyan)
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, "Añadir a lista", tint = Purple)
        }
    }
    if (showGenre) {
        EditGenreDialog(song) { showGenre = false }
    }
}

// Diálogo para cambiar el género de una canción (manual, siempre funciona)
@Composable
fun EditGenreDialog(song: Song, onDismiss: () -> Unit) {
    val existing = MusicRepository.genresWithCount().map { it.first }
    val suggestions = listOf(
        "Pop", "Rock", "Balada", "Salsa", "Cumbia", "Banda", "Grupera",
        "Norteño", "Corridos", "Ranchera", "Vallenato", "Reggaetón",
        "Bachata", "Merengue", "Electrónica", "Bandas Sonoras"
    )
    val options = (existing + suggestions).distinct().filter { it != "Otros" }
    var custom by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Cambiar género", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(song.title, color = Cyan, fontSize = 13.sp, maxLines = 1)
                Text("Género actual: ${song.genre}", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                ) {
                    options.chunked(3).forEach { rowOptions ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            rowOptions.forEach { g ->
                                val isCurrent = g.equals(song.genre, ignoreCase = true)
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isCurrent) Purple.copy(alpha = 0.35f) else CardLighter)
                                        .clickable {
                                            MusicRepository.updateGenre(song.id, g)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(g, color = Color.White, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Otro género…", color = TextSecondary, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = Color.White, focusedBorderColor = Purple,
                        unfocusedBorderColor = TextSecondary
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (custom.isNotBlank()) {
                                MusicRepository.updateGenre(song.id, custom.trim())
                                onDismiss()
                            }
                        }) { Icon(Icons.Filled.Check, null, tint = Pink) }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Purple) }
        }
    )
}

// Diálogo para elegir (o crear) una lista y añadir la canción
@Composable
fun AddToPlaylistDialog(song: Song?, onDismiss: () -> Unit) {
    if (song == null) return
    val lists by PlaylistRepository.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Añadir a lista", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(song.title, color = Cyan, fontSize = 13.sp, maxLines = 1)
                Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(12.dp))

                if (lists.isEmpty()) {
                    Text(
                        "Aún no tienes listas.\nCrea una con el nombre de abajo:",
                        color = TextSecondary, fontSize = 13.sp
                    )
                } else {
                    Text("Tus listas:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    lists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val ok = PlaylistRepository.addSong(pl.id, song.id)
                                    msg = if (ok) "Añadida a «${pl.name}» ✓"
                                    else "Ya estaba en «${pl.name}»"
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null,
                                tint = Purple, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(pl.name, color = Color.White, fontSize = 14.sp,
                                modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${pl.songIds.size}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Crear lista nueva…", color = TextSecondary, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
