package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.components.SongRow
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

// Pantalla de un artista: muestra todas sus canciones y permite
// reproducirlas de una vez (normal o en aleatorio).
@Composable
fun ArtistSongsScreen(nav: NavHostController, vm: PlayerViewModel, artistName: String) {
    val songs by MusicRepository.songs.collectAsState()
    // Filtrar solo cuando cambian la biblioteca o el artista
    // (antes se repetía en cada recomposición de la pantalla)
    val list = remember(songs, artistName) { songs.filter { it.artist == artistName } }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    val cover = list.firstOrNull { it.coverPath.isNotBlank() } ?: list.firstOrNull()

    Column(Modifier.fillMaxSize().background(NavyBg)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.popBackStack() })
                Text("  Artista", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (list.size == 1) "1 canción" else "${list.size} canciones",
                    color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            if (list.isEmpty()) {
                // ---------- Este artista aún no tiene canciones en la biblioteca ----------
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(50.dp))
                    Box(Modifier.size(96.dp).clip(CircleShape).background(CardLighter),
                        contentAlignment = Alignment.Center) {
                        Text(artistName.take(1).uppercase(), color = Color.White,
                            fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(artistName, color = Color.White, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Este artista no tiene canciones en tu biblioteca todavía.",
                        color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                // ---------- Cabecera del artista ----------
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverImage(cover, Modifier.size(110.dp).clip(CircleShape), sample = 2)
                    Spacer(Modifier.height(12.dp))
                    Text(artistName, color = Color.White, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                    Text(if (list.size == 1) "1 canción" else "${list.size} canciones",
                        color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))

                    // ---------- Reproducir todo / Aleatorio ----------
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GradientBrush)
                                .clickable { vm.play(list.first(), list) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reproducir todo", color = Color.White,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardLighter)
                                .clickable {
                                    if (vm.repeatOne) vm.toggleRepeat()
                                    val mezcla = list.shuffled()
                                    vm.play(mezcla.first(), mezcla)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shuffle, null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Aleatorio", color = Color.White,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ---------- Lista de canciones ----------
                LazyColumn {
                    items(list.size) { i ->
                        val s = list[i]
                        SongRow(song = s, list = list, vm = vm, onAdd = { songToAdd = s })
                    }
                }
            }
        }
        if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
    }

    AddToPlaylistDialog(songToAdd) { songToAdd = null }
}
