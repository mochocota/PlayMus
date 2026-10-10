package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.musica.data.forYou
import com.example.musica.data.recentHome
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.components.SectionHeader
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun HomeScreen(nav: NavHostController, vm: PlayerViewModel) {
    val songs by MusicRepository.songs.collectAsState()
    val library = songs.ifEmpty { ff6Songs }

    Column(Modifier.fillMaxSize().background(NavyBg)
        .verticalScroll(rememberScrollState()).padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Inicio", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Search, null, tint = Color.White,
                modifier = Modifier.clickable { nav.navigate("search") })
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(34.dp).clip(CircleShape).background(CardLighter),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = Cyan)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Buenas vibras,\nsiempre 🎵", color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF241A3E), Color(0xFF12305A))))
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hoy es un gran día", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("para escuchar algo épico", color = TextSecondary, fontSize = 13.sp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(GradientBrush)
                .clickable {
                    vm.play(library.first(), library)
                    nav.navigate("player")
                },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Escuchado recientemente", "Ver todo")
        Spacer(Modifier.height(12.dp))
        if (songs.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                songs.take(6).forEach { s ->
                    Column(Modifier.width(110.dp).clickable {
                        vm.play(s, songs)
                        nav.navigate("player")
                    }) {
                        CoverImage(s, Modifier.size(110.dp), corner = 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(s.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(s.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentHome.forEach { (name, kind) ->
                    Column(Modifier.width(110.dp)) {
                        AlbumArt(Modifier.size(110.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(kind, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Para ti", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            forYou.forEach { f ->
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(f.gradient)
                    .clickable { nav.navigate("playlist/${f.id}") },
                    contentAlignment = Alignment.BottomStart) {
                    Text(f.name, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
