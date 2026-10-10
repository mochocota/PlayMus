package com.example.musica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musica.data.MusicRepository
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun AlbumArt(modifier: Modifier = Modifier, corner: Dp = 14.dp, gradient: Brush = GradientBrush) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(gradient), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = Purple, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
fun Waveform(color: Color = Purple) {
    val heights = listOf(8, 20, 12, 28, 16, 30, 10, 24, 14, 26, 9, 22)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEach { h ->
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song = vm.current ?: return
    // Busca la versión fresca (con carátula) en el repositorio
    val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(CardLighter)
            .clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(fresh, Modifier.size(40.dp), corner = 10.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
            Text(song.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = { vm.toggle() }) {
            Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
        }
        IconButton(onClick = { vm.next() }) {
            Icon(Icons.Filled.SkipNext, null, tint = Color.White)
        }
    }
}
