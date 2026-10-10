package com.example.musica.ui.screens

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
import com.example.musica.data.fmtMillis
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import com.example.musica.ui.theme.TrackColor

@Composable
fun NowPlayingScreen(nav: NavHostController, vm: PlayerViewModel) {
    val song = vm.current
    var liked by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxSize().background(NavyBg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
            }
            Text("Reproduciendo ahora", color = Color.White,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(16.dp))

        if (song == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Elige una canción para empezar", color = TextSecondary)
            }
            return
        }

        // Versión fresca del repositorio (para ver la carátula en cuanto se procese)
        val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song

        CoverImage(fresh, Modifier.fillMaxWidth().aspectRatio(1f), corner = 20.dp, sample = 1)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${song.album.ifBlank { song.genre }} · ${song.artist}",
                    color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
            IconButton(onClick = { liked = !liked }) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (liked) Pink else Color.White)
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
                Icon(Icons.Filled.Shuffle, null, tint = if (vm.shuffle) Purple else Color.White)
            }
            IconButton(onClick = { vm.prev() }) {
                Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Box(Modifier.size(68.dp).clip(CircleShape).background(GradientBrush)
                .clickable { vm.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                    tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { vm.toggleRepeat() }) {
                Icon(Icons.Filled.Repeat, null, tint = if (vm.repeatOne) Purple else Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = {}) { Icon(Icons.Filled.Cast, null, tint = TextSecondary) }
            IconButton(onClick = { nav.navigate("queue") }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = TextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(CardLighter)
            .padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("Alta calidad", color = Cyan, fontSize = 12.sp)
        }
    }
}
