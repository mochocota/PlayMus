package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.playlists
import com.example.musica.data.recents
import com.example.musica.player.PlayerViewModel
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.SectionHeader
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(nav: NavHostController, vm: PlayerViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val songs by MusicRepository.songs.collectAsState()
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var autoTried by rememberSaveable { mutableStateOf(false) }

    fun doSync() {
        if (syncing) return
        syncing = true
        status = "Sincronizando…"
        scope.launch {
            status = try { MusicRepository.sync() } catch (e: Exception) { "Error: ${e.message}" }
            syncing = false
        }
    }

    LaunchedEffect(Unit) {
        if (!autoTried) {
            autoTried = true
            if (TelegramConfig.token(ctx).isNotBlank() && MusicRepository.songs.value.isEmpty()) {
                doSync()
            }
        }
    }

    // Auto-escaneo: mientras haya canciones sin procesar (carátula/género), procesa tandas
    LaunchedEffect(Unit) {
        while (isActive && MusicRepository.pendingCount() > 0) {
            try { MusicRepository.scanPending() } catch (_: Exception) {}
            delay(600)
        }
    }

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { doSync() }, enabled = !syncing) {
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Purple)
                else Icon(Icons.Filled.Refresh, null, tint = Color.White)
            }
            Icon(Icons.Filled.Search, null, tint = Color.White)
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Filled.Settings, null, tint = Color.White,
                modifier = Modifier.clickable { nav.navigate("settings") })
        }
        Spacer(Modifier.height(10.dp))

        if (TelegramConfig.token(ctx).isBlank()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardLighter)
                .clickable { nav.navigate("telegram") }.padding(12.dp)) {
                Text("📻 Conecta tu canal de Telegram para llenar tu biblioteca",
                    color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
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

        val rows: List<Pair<Triple<ImageVector, String, String>, String?>> = listOf(
            Triple(Icons.Filled.MusicNote, "Canciones", "${songs.size}") to "genre/Todas",
            Triple(Icons.Filled.Album, "Álbumes", "${MusicRepository.albumCount()}") to "albums",
            Triple(Icons.Filled.Person, "Artistas", "${MusicRepository.artistCount()}") to "artists",
            Triple(Icons.Filled.Category, "Géneros", "${MusicRepository.genresWithCount().size}") to "genres",
            Triple(Icons.AutoMirrored.Filled.QueueMusic, "Listas de reproducción", "${playlists.size}") to "playlists",
            Triple(Icons.Filled.Cloud, "Canal de Telegram",
                if (TelegramConfig.token(ctx).isBlank()) "Configurar" else "Conectado") to "telegram"
        )
        rows.forEach { (t, route) ->
            val (icon, label, count) = t
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark)
                .clickable(enabled = route != null) { route?.let { nav.navigate(it) } }
                .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(CardLighter),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text(label, color = Color.White, modifier = Modifier.weight(1f))
                Text(count, color = if (label == "Canal de Telegram" && TelegramConfig.token(ctx).isBlank())
                    Pink else TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Tus canciones", "Ver todo")
        if (songs.isNotEmpty()) {
            songs.take(4).forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { vm.play(s, songs) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(s, Modifier.size(46.dp), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Icon(Icons.Filled.MoreVert, null, tint = TextSecondary)
                }
            }
        } else {
            recents.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(Modifier.size(46.dp), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = Color.White, fontSize = 14.sp)
                        Text(r.subtitle, color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.MoreVert, null, tint = TextSecondary)
                }
            }
        }
    }
}
