package com.example.musica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.FavoritesRepository
import com.example.musica.data.LyricLine
import com.example.musica.data.LyricsRepository
import com.example.musica.data.LyricsResult
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.data.fmtMillis
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import com.example.musica.ui.theme.TrackColor
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(nav: NavHostController, vm: PlayerViewModel) {
    val song = vm.current
    val favIds by FavoritesRepository.ids.collectAsState()
    val liked = song != null && song.id in favIds
    var showFavAnim by remember { mutableStateOf(false) }
    var favMsg by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // ---------- Estado de la letra ----------
    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LyricsResult?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsFail by remember { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(showFavAnim) {
        if (showFavAnim) {
            delay(1300)
            showFavAnim = false
        }
    }

    // Carga la letra al abrir el modo letra (o al cambiar de canción con la letra abierta)
    LaunchedEffect(showLyrics, song?.id, retry) {
        val s = song ?: return@LaunchedEffect
        if (showLyrics && loadedFor != s.id) {
            lyricsLoading = true
            lyricsFail = false
            val res = try { LyricsRepository.getFor(s) } catch (_: Exception) { null }
            lyrics = res
            lyricsFail = res == null
            loadedFor = s.id
            lyricsLoading = false
        }
    }

    Box(Modifier.fillMaxSize().background(NavyBg)) {

        if (showLyrics && song != null) {
            // ================= MODO LETRA =================
            LyricsView(
                vm = vm, song = song,
                lyrics = lyrics, loading = lyricsLoading, failed = lyricsFail,
                onClose = { showLyrics = false },
                onRetry = { loadedFor = null; retry++ }
            )
        } else {
            // ================= VISTA NORMAL =================
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
                    }
                    Text("Reproduciendo ahora", color = Color.White,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(48.dp))
                }

                if (song == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Elige una canción para empezar", color = TextSecondary)
                    }
                } else {
                    Spacer(Modifier.weight(1f))

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song
                        CoverImage(fresh, Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = 20.dp, sample = 1)
                        Spacer(Modifier.height(20.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${song.album.ifBlank { song.genre }} · ${song.artist}",
                                    color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                val nowFav = FavoritesRepository.toggle(song.id)
                                if (nowFav) {
                                    favMsg = "Agregado a favoritos"
                                    showFavAnim = true
                                } else {
                                    favMsg = "Eliminado de favoritos"
                                    showFavAnim = true
                                }
                            }) {
                                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    null, tint = if (liked) Pink else Color.White)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val progress = if (dragging) dragValue
                        else if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f

                        Slider(
                            value = progress.coerceIn(0f, 1f),
                            onValueChange = { dragging = true; dragValue = it },
                            onValueChangeFinished = { vm.seekTo(dragValue); dragging = false },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = TrackColor
                            )
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmtMillis(vm.position), color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(fmtMillis(vm.duration), color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.toggleShuffle() }) {
                                Icon(Icons.Filled.Shuffle, null,
                                    tint = if (vm.shuffle) Purple else Color.White)
                            }
                            IconButton(onClick = { vm.prev() }) {
                                Icon(Icons.Filled.SkipPrevious, null, tint = Color.White,
                                    modifier = Modifier.size(34.dp))
                            }
                            Box(Modifier.size(68.dp).clip(CircleShape).background(GradientBrush)
                                .clickable { vm.toggle() }, contentAlignment = Alignment.Center) {
                                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            IconButton(onClick = { vm.next() }) {
                                Icon(Icons.Filled.SkipNext, null, tint = Color.White,
                                    modifier = Modifier.size(34.dp))
                            }
                            IconButton(onClick = { vm.toggleRepeat() }) {
                                Icon(Icons.Filled.Repeat, null,
                                    tint = if (vm.repeatOne) Purple else Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {}) {
                                Icon(Icons.Filled.Cast, null, tint = TextSecondary)
                            }
                            IconButton(onClick = { showLyrics = true }) {
                                Icon(Icons.Filled.Lyrics, "Ver letra",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = { songToAdd = song }) {
                                Icon(Icons.Filled.PlaylistAdd, "Añadir a lista",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            IconButton(onClick = { nav.navigate("queue") }) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(CardLighter)
                            .padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Text("Alta calidad", color = Cyan, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        // ---------- Animación de favoritos ----------
        AnimatedVisibility(
            visible = showFavAnim && song != null && !showLyrics,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.5f, animationSpec = tween(250)) +
                    fadeIn(animationSpec = tween(250)),
            exit = scaleOut(targetScale = 1.35f, animationSpec = tween(350)) +
                    fadeOut(animationSpec = tween(350))
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp)).background(CardDark)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (favMsg == "Agregado a favoritos") Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (favMsg == "Agregado a favoritos") Pink else TextSecondary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(favMsg, color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    AddToPlaylistDialog(songToAdd) { songToAdd = null }
}

// ============================================================
// Vista de letra
// ============================================================
@Composable
private fun LyricsView(
    vm: PlayerViewModel,
    song: Song,
    lyrics: LyricsResult?,
    loading: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, null, tint = Color.White)
            }
            Text("Letra", color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Purple)
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando letra…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                failed -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("♪", color = TextSecondary, fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No se encontró la letra de esta canción",
                            color = TextSecondary, fontSize = 14.sp,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Reintentar", color = Purple, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                else -> {
                    val synced = lyrics?.synced.orEmpty()
                    val plain = lyrics?.plain
                    if (synced.isNotEmpty()) {
                        SyncedLyrics(synced, vm)
                    } else if (plain != null) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(vertical = 20.dp)
                        ) {
                            Text(plain, color = Color.White, fontSize = 16.sp,
                                lineHeight = 26.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("No se encontró la letra de esta canción",
                            color = TextSecondary, fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CardLighter).clickable(onClick = onClose).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CoverImage(fresh, Modifier.size(40.dp), corner = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Filled.KeyboardArrowUp, "Volver al reproductor", tint = TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text("Letra por LRCLIB", color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 10.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
    }
}

// Letra sincronizada
@Composable
private fun SyncedLyrics(lines: List<LyricLine>, vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    var current by remember { mutableStateOf(-1) }

    LaunchedEffect(vm.position, lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        val idx = lines.indexOfLast { it.timeMs <= vm.position }
        if (idx != current) {
            current = idx
            if (idx >= 0) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(lines.size) { i ->
            val active = i == current
            Text(
                text = lines[i].text.ifBlank { "♪" },
                color = if (active) Color.White else TextSecondary.copy(alpha = 0.75f),
                fontSize = if (active) 19.sp else 15.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                lineHeight = if (active) 26.sp else 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = vm.duration > 0) {
                        vm.seekTo(lines[i].timeMs.toFloat() / vm.duration)
                    }
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            )
        }
    }
}
