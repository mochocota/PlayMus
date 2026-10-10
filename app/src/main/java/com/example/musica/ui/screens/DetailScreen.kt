package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.fmtSeconds
import com.example.musica.data.playlists
import com.example.musica.data.songsFor
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun DetailScreen(nav: NavHostController, vm: PlayerViewModel, id: String) {
    val playlist = playlists.firstOrNull { it.id == id }
    val title = playlist?.name ?: "Playlist"
    val songs = songsFor(id)
    val subtitle = if (id == "chill") "64 canciones · 3h 52m" else "Various Artists · 1994 · RPG"

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.MoreVert, null, tint = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            AlbumArt(Modifier.fillMaxWidth(0.6f).aspectRatio(1f), corner = 18.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(GradientBrush)
                    .clickable { vm.play(songs.first(), songs) },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                }
                Icon(Icons.Filled.FavoriteBorder, null, tint = TextSecondary)
                Icon(Icons.Filled.Download, null, tint = TextSecondary)
                Icon(Icons.Filled.MoreVert, null, tint = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            songs.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clickable { vm.play(s, songs) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}",
                        color = if (vm.current?.id == s.id) Purple else TextSecondary,
                        fontSize = 14.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }
}
