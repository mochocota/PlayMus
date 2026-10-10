package com.example.musica.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.musica.data.fmtSeconds
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun AlbumsScreen(nav: NavHostController) {
    val songs by MusicRepository.songs.collectAsState()
    val albums = MusicRepository.albums()

    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Álbumes", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${albums.size}", color = TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (albums.isEmpty()) {
            Text("Tu biblioteca está vacía.\n\nSincroniza tu canal de Telegram en la Biblioteca.",
                color = TextSecondary, fontSize = 14.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums.size) { i ->
                    val (name, list) = albums[i]
                    val cover = list.firstOrNull { it.coverPath.isNotBlank() } ?: list.first()
                    Column(Modifier.clickable {
                        nav.navigate("album/" + Uri.encode(name))
                    }) {
                        CoverImage(cover, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(name, color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${list.size} canciones", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumSongsScreen(nav: NavHostController, vm: PlayerViewModel, albumName: String) {
    val songs by MusicRepository.songs.collectAsState()
    val list = songs.filter { (it.album.ifBlank { "Sencillos" }) == albumName }
    val cover = list.firstOrNull { it.coverPath.isNotBlank() } ?: list.firstOrNull()

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Spacer(Modifier.weight(1f))
                Text("Álbum", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            CoverImage(cover, Modifier.fillMaxWidth(0.6f).aspectRatio(1f), corner = 18.dp, sample = 1)
            Spacer(Modifier.height(12.dp))
            Text(albumName, color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(list.firstOrNull()?.artist ?: "", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            list.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clickable { vm.play(s, list) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}",
                        color = if (vm.current?.id == s.id) Purple else TextSecondary,
                        fontSize = 14.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(s.genre, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }
}
