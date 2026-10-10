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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
    var liked by remember { mutableStateOf(false) }
    var showFavAnim by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // Oculta la animación de favoritos después de un momento
    LaunchedEffect(showFavAnim) {
        if (showFavAnim) {
            delay(1300)
            showFavAnim = false
        }
    }

    Box(Modifier.fillMaxSize().background(NavyBg)) {

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

            // ---------- Encabezado ----------
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
                // Espacio flexible arriba (igual que abajo)
                Spacer(Modifier.weight(1f))

                // ---------- Bloque central: carátula + info + controles ----------
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
                            liked = !liked
                            if (liked) showFavAnim = true
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

                // Espacio flexible abajo (igual que arriba)
                Spacer(Modifier.weight(1f))

                // ---------- Bloque inferior ----------
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Cast, null, tint = TextSecondary)
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

        // ---------- Animación de favoritos ----------
        AnimatedVisibility(
            visible = showFavAnim && song != null,
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
                Icon(Icons.Filled.Favorite, null, tint = Pink, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(10.dp))
                Text("Agregado a favoritos", color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ---------- Diálogo: añadir la canción a una lista (o crear una nueva) ----------
    AddToPlaylistDialog(songToAdd) { songToAdd = null }
}
