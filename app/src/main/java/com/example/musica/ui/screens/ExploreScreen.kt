package com.example.musica.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import java.text.Normalizer

private fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().trim()

// Nombre único para no chocar con SearchItem de SearchScreen.kt
private data class ExploreItem(
    val song: Song,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String
)

@Composable
fun ExploreScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRaw by MusicRepository.songs.collectAsState()
    val offline by DownloadRepository.offlineMode.collectAsState()
    val library = if (offline) songsRaw.filter { DownloadRepository.isDownloaded(it.id) }
    else songsRaw

    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    // Índice precalculado (rápido con miles de canciones)
    val index = remember(library) {
        library.map {
            ExploreItem(it, norm(it.title), norm(it.artist), norm(it.album), norm(it.genre))
        }
    }

    val nq = norm(query)
    val hasQuery = query.isNotBlank()

    // ---------- Sugerencias (lista con 🔎, como la imagen) ----------
    val suggestions = if (hasQuery) buildList {
        index.forEach { if (it.title.contains(nq)) add(it.song.title) }
        index.forEach { if (it.artist.contains(nq)) add(it.song.artist) }
        index.forEach { if (it.album.isNotEmpty() && it.album.contains(nq)) add(it.song.album) }
        index.forEach { if (it.genre.contains(nq)) add(it.song.genre) }
    }.distinctBy { norm(it) }.filter { norm(it) != nq }.take(6)
    else emptyList()

    // ---------- Resultados en filas ----------
    val results = if (hasQuery) index.filter {
        it.title.contains(nq) || it.artist.contains(nq) ||
                it.album.contains(nq) || it.genre.contains(nq)
    }.map { it.song }
    else emptyList()

    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var songMenu by remember { mutableStateOf<Song?>(null) }
    var songToDownload by remember { mutableStateOf<Song?>(null) }
    val dlVersion by DownloadRepository.version.collectAsState()
    val dlProgress by DownloadRepository.progress.collectAsState()

    Column(Modifier.fillMaxSize().background(NavyBg)) {

        // ---------- Barra de búsqueda (estilo YouTube) ----------
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White,
                modifier = Modifier
                    .clickable {
                        if (searching) {
                            searching = false
                            query = ""
                        } else nav.popBackStack()
                    }
                    .padding(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    searching = it.isNotBlank()
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar en Explorar…", color = TextSecondary, fontSize = 15.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    cursorColor = Color.White, focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Close, "Limpiar", tint = Color.White)
                }
            }
        }

        // ---------- Contenido ----------
        when {
            // ============ MODO BÚSQUEDA ============
            searching && hasQuery -> {

                // Sugerencias (lista con 🔎 y flechita, como la imagen)
                if (suggestions.isNotEmpty()) {
                    suggestions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { query = s }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, tint = TextSecondary,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(s, color = Color.White, fontSize = 15.sp,
                                modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Resultados en filas (carátula · título/artista · ⋮)
                if (results.isNotEmpty()) {
                    Text(
                        if (offline) "✈️ Offline · ${results.size} resultados"
                        else "Resultados (${results.size})",
                        color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyColumn {
                        items(results.size) { i ->
                            val s = results[i]
                            val downloading = dlProgress.containsKey(s.id)
                            SongRowWithMenu(
                                song = s,
                                downloading = downloading,
                                menuOpen = songMenu?.id == s.id,
                                onMenuOpen = { songMenu = s },
                                onMenuDismiss = { songMenu = null },
                                onClick = { vm.play(s, results) },
                                onPlay = { vm.play(s, results) },
                                onAdd = { songToAdd = s },
                                onDownload = {
                                    DownloadRepository.startDownload(s) { r ->
                                        Toast.makeText(context,
                                            if (r == "ok") "Descarga completa ✓" else r,
                                            Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDelete = { songToDownload = s },
                                onArtist = { nav.navigate("artist/" + Uri.encode(s.artist)) }
                            )
                        }
                    }
                } else {
                    Text("Sin resultados para «$query»",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            // ============ MODO EXPLORAR (sin búsqueda) ============
            else -> {

                // Banner "Descubre nuevos sonidos" (diseño de siempre)
                Box(Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(
                        listOf(Color(0xFF5B21B6), Color(0xFFBE185D))))
                    .padding(18.dp)) {
                    Text("Descubre\nnuevos sonidos", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))

                if (offline) {
                    Text("✈️ Modo offline · ${library.size} descargadas",
                        color = Cyan, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                }

                if (library.isEmpty()) {
                    Text(
                        if (offline) "Modo offline activo y sin descargas.\nDesactívalo en Configuración."
                        else "Tu biblioteca está vacía.\nSincroniza tu canal de Telegram en Biblioteca.",
                        color = TextSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    Text("Toda tu música (${library.size})",
                        color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    LazyColumn {
                        items(library.size) { i ->
                            val s = library[i]
                            SongRowWithMenu(
                                song = s,
                                downloading = false,
                                menuOpen = songMenu?.id == s.id,
                                onMenuOpen = { songMenu = s },
                                onMenuDismiss = { songMenu = null },
                                onClick = { vm.play(s, library) },
                                onPlay = { vm.play(s, library) },
                                onAdd = { songToAdd = s },
                                onDownload = {
                                    DownloadRepository.startDownload(s) { r ->
                                        Toast.makeText(context,
                                            if (r == "ok") "Descarga completa ✓" else r,
                                            Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDelete = { songToDownload = s },
                                onArtist = { nav.navigate("artist/" + Uri.encode(s.artist)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------- Diálogos ----------
    songToAdd?.let { s ->
        AddToPlaylistDialog(s) { songToAdd = null }
    }

    songToDownload?.let { s ->
        AlertDialog(
            onDismissRequest = { songToDownload = null },
            containerColor = CardDark,
            title = { Text("Eliminar descarga", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar «${s.title}» del teléfono?",
                color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadRepository.delete(s.id)
                    songToDownload = null
                }) { Text("Eliminar", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { songToDownload = null }) { Text("Cancelar", color = Purple) }
            }
        )
    }
}

// ============================================================
// Fila de resultado: carátula · título/artista · ⋮ con menú.
// El estado del menú viene por parámetros (vive en el exterior).
// Tocar el nombre del artista abre su pantalla.
// ============================================================
@Composable
private fun SongRowWithMenu(
    song: Song,
    downloading: Boolean,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onArtist: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(song, Modifier.size(52.dp), corner = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(song.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier
                    .clickable { onArtist() }
                    .padding(vertical = 2.dp))
        }
        Box {
            IconButton(onClick = onMenuOpen) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White)
            }
            if (menuOpen) {
                val downloaded = DownloadRepository.isDownloaded(song.id)
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = onMenuDismiss,
                    containerColor = CardDark
                ) {
                    DropdownMenuItem(
                        text = { Text("Reproducir", color = Color.White) },
                        onClick = { onMenuDismiss(); onPlay() }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a lista", color = Color.White) },
                        onClick = { onMenuDismiss(); onAdd() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (downloading) "Descargando…"
                                else if (downloaded) "Eliminar descarga"
                                else "Descargar",
                                color = if (downloading) Cyan else Color.White
                            )
                        },
                        onClick = {
                            onMenuDismiss()
                            if (downloading) { /* en curso */ }
                            else if (downloaded) onDelete()
                            else onDownload()
                        }
                    )
                }
            }
        }
    }
}
