package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.MusicRepository
import com.example.musica.data.ff6Songs
import com.example.musica.data.genreColor
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

@Composable
fun ExploreScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songsRepo by MusicRepository.songs.collectAsState()
    val library = songsRepo.ifEmpty { ff6Songs }
    var selected by remember { mutableStateOf("Todas") }

    // Géneros reales de la biblioteca, con su cantidad
    val genres = library.groupingBy { it.genre }.eachCount().toList()
        .sortedBy { it.first.lowercase() }
    val visible = if (selected == "Todas") library
        else library.filter { it.genre == selected }

    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {

        // ---------- Encabezado (sin lupa) ----------
        Text("Explorar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // ---------- Banner (solo "Descubre nuevos sonidos") ----------
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF5B21B6), Color(0xFFBE185D))))
            .padding(18.dp)) {
            Text("Descubre\nnuevos sonidos", color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        // ---------- Filtros por género (dinámicos) ----------
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenreChip(
                label = "Todas", count = library.size,
                isSelected = selected == "Todas", color = Purple
            ) { selected = "Todas" }
            genres.forEach { (name, count) ->
                GenreChip(
                    label = name, count = count,
                    isSelected = selected == name, color = genreColor(name)
                ) { selected = name }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${visible.size} canciones", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        // ---------- Cuadrícula de 3 columnas ----------
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(visible.size) { i ->
                val s = visible[i]
                Column(Modifier.clickable { vm.play(s, visible) }) {
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

@Composable
private fun GenreChip(label: String, count: Int, isSelected: Boolean,
                      color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) color else CardLighter)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$count",
            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
            fontSize = 11.sp)
    }
}
