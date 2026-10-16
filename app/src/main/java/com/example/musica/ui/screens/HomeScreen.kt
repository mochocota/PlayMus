package com.example.musica.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRaw by MusicRepository.songs.collectAsState()
    val offline by DownloadRepository.offlineMode.collectAsState()
    val songs = if (offline) songsRaw.filter { DownloadRepository.isDownloaded(it.id) }
    else songsRaw

    val stats by PlayStatsRepository.counts.collectAsState()
    val lastPlayed by PlayStatsRepository.lastPlayed.collectAsState()

    // Escuchado recientemente (hasta 15)
    val recentSongs = remember(songs, lastPlayed) {
        lastPlayed.entries.sortedByDescending { it.value }
            .mapNotNull { e -> songs.firstOrNull { it.id == e.key } }
            .take(15)
    }

    // Las más reproducidas (hasta 15)
    val topSongs = remember(songs, stats) {
        songs.filter { (stats[it.id] ?: 0) > 0 }
            .sortedByDescending { stats[it.id] ?: 0 }
            .take(15)
    }

    // Te podría gustar (hasta 15)
    val recommended = remember(songs, topSongs) {
        if (topSongs.isEmpty()) emptyList()
        else {
            val topIds = topSongs.map { it.id }.toSet()
            val pool = songs.filter { it.id !in topIds }
            val topGenres = topSongs.map { it.genre }.toSet()
            val topArtists = topSongs.map { it.artist }.toSet()
            val similar = pool.filter { it.genre in topGenres || it.artist in topArtists }.shuffled()
            val similarIds = similar.map { it.id }.toSet()
            val others = pool.filter { it.id !in similarIds }.shuffled()
            (similar + others).take(15)
        }
    }

    // Abrir la pantalla de un artista desde su nombre
    val openArtist: (String) -> Unit = { name ->
        nav.navigate("artist/" + Uri.encode(name))
    }

    // ---------- Cuadrícula perezosa de 3 columnas ----------
    // Antes cada sección era una lista de filas chunked(3) dentro de un
    // Column con verticalScroll: TODO se medía y componía de una sola vez.
    // Ahora solo se compone lo visible y el scroll es mucho más fluido.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(NavyBg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- Encabezado + banner ----------
        item(key = "titulo", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Inicio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF5B21B6), Color(0xFFBE185D))))
                    .padding(18.dp)) {
                    Text("Dale play a tu día 😃", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------- Escuchado recientemente ----------
        seccionTitulo("Escuchado recientemente")
        if (recentSongs.isEmpty()) {
            item(key = "vacio_recientes", span = { GridItemSpan(maxLineSpan) }) {
                Text("Reproduce canciones y aparecerán aquí.",
                    color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            items(recentSongs.size, key = { "rec_" + recentSongs[it].id }) { i ->
                CeldaCancion(recentSongs[i], recentSongs, openArtist) { s, list ->
                    vm.play(s, list)
                    nav.navigate("player")
                }
            }
        }

        // ---------- Las más reproducidas ----------
        seccionTitulo("Las más reproducidas")
        if (topSongs.isEmpty()) {
            item(key = "vacio_top", span = { GridItemSpan(maxLineSpan) }) {
                Text("Reproduce canciones y aquí aparecerán tus más escuchadas.",
                    color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            items(topSongs.size, key = { "top_" + topSongs[it].id }) { i ->
                CeldaCancion(topSongs[i], topSongs, openArtist) { s, list ->
                    vm.play(s, list)
                    nav.navigate("player")
                }
            }
        }

        // ---------- Te podría gustar ----------
        seccionTitulo("Te podría gustar")
        if (recommended.isEmpty()) {
            item(key = "vacio_recom", span = { GridItemSpan(maxLineSpan) }) {
                Text("Cuando reproduzcas más canciones te sugeriremos música parecida.",
                    color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            items(recommended.size, key = { "gusta_" + recommended[it].id }) { i ->
                CeldaCancion(recommended[i], recommended, openArtist) { s, list ->
                    vm.play(s, list)
                    nav.navigate("player")
                }
            }
        }
    }
}

// ---------- Título de sección a lo ancho de la cuadrícula ----------
private fun LazyGridScope.seccionTitulo(titulo: String) {
    item(key = "titulo_$titulo", span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Spacer(Modifier.height(14.dp))
            Text(titulo, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------- Tarjeta de canción (3 por fila) ----------
// Tocar la tarjeta reproduce la canción; tocar el nombre del
// artista abre su pantalla.
@Composable
private fun CeldaCancion(
    s: Song,
    list: List<Song>,
    onArtist: (String) -> Unit,
    onPlay: (Song, List<Song>) -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable { onPlay(s, list) }) {
        CoverImage(s, Modifier.fillMaxWidth().aspectRatio(1f),
            corner = 12.dp, offlineBadge = true)
        Spacer(Modifier.height(6.dp))
        Text(s.title, color = Color.White, fontSize = 12.sp,
            maxLines = 1, lineHeight = 14.sp)
        Text(s.artist, color = TextSecondary, fontSize = 10.sp,
            maxLines = 1, lineHeight = 12.sp,
            modifier = Modifier
                .clickable { onArtist(s.artist) }
                .padding(vertical = 2.dp))
    }
}
