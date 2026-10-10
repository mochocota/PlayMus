package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.ArtistItem
import com.example.musica.data.MusicRepository
import com.example.musica.data.artists
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun ArtistsScreen(nav: NavHostController) {
    val songs by MusicRepository.songs.collectAsState()
    val repoArtists = songs.groupBy { it.artist }
        .map { (name, list) -> ArtistItem(name, list.size, name.take(1).uppercase()) }
    val list = if (repoArtists.isNotEmpty()) repoArtists else artists

    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Artistas", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Search, null, tint = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(list.size) { i ->
                val a = list[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(CardLighter),
                        contentAlignment = Alignment.Center) {
                        Text(a.emoji, fontSize = 34.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                    Text("${a.count} canciones", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(CardLighter), contentAlignment = Alignment.Center) {
            Text("Ver todos los artistas", color = Color.White)
        }
    }
}
