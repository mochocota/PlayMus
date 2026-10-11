package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.data.playlists
import com.example.musica.player.PlayerViewModel
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(nav: NavHostController, vm: PlayerViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val songs by MusicRepository.songs.collectAsState()
    val userLists by PlaylistRepository.playlists.collectAsState()
    val ready by MusicRepository.ready.collectAsState()
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun doSync() {
        if (syncing) return
        syncing = true
        status = "Sincronizando…"
        scope.launch {
            status = try { MusicRepository.sync() } catch (e: Exception) { "Error: ${e.message}" }
            syncing = false
        }
    }

    Column(Modifier.fillMaxSize().background(NavyBg)) {

        // ---------- Parte superior (scrolleable) ----------
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)) {

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Biblioteca", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { doSync() }, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(18.dp),
                        strokeWidth = 2.dp, color = Purple)
                    else Icon(Icons.Filled.Refresh, null, tint = Color.White)
                }
                IconButton(onClick = { nav.navigate("search") }) {
                    Icon(Icons.Filled.Search, null, tint = Color.White)
                }
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Settings, null, tint = Color.White,
                    modifier = Modifier.clickable { nav.navigate("settings") })
            }
            Spacer(Modifier.height(10.dp))

            if (status.isNotBlank()) {
                Text(status, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            val pending = MusicRepository.pendingCount()
            if (pending > 0) {
                Text("🔎 Procesando carátulas y géneros… faltan $pending canciones",
                    color = Purple, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            val onlinePending = MusicRepository.pendingOnlineCount()
            if (pending == 0 && onlinePending > 0) {
                Text("🌐 Buscando géneros en internet… faltan $onlinePending canciones",
                    color = Cyan, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))

            // ---------- Acceso a las listas ----------
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark)
                .clickable { nav.navigate("playlists") }
                .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(CardLighter),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text("Listas de reproducción", color = Color.White, modifier = Modifier.weight(1f))
                Text("${userLists.size + 1}", color = TextSecondary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---------- Cuadrícula de canciones (3 columnas) ----------
        if (songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center) {
                if (!ready) {
                    CircularProgressIndicator(color = Purple)
                } else {
                    Text(
                        "Tu biblioteca está vacía.\n\nSi el token está integrado en la app, la sincronización empieza sola; también puedes pulsar 🔄.",
                        color = TextSecondary, fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(songs.size) { i ->
                    val s = songs[i]
                    Column(Modifier.clickable { vm.play(s, songs) }) {
                        CoverImage(s, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(s.title, color = Color.White, fontSize = 12.sp,
                            maxLines = 1, lineHeight = 14.sp)
                        Text(s.artist, color = TextSecondary, fontSize = 10.sp,
                            maxLines = 1, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}
