package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.genres
import com.example.musica.data.trends
import com.example.musica.ui.components.SectionHeader
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg

@Composable
fun ExploreScreen(nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().background(NavyBg)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explorar", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Search, null, tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF5B21B6), Color(0xFFBE185D))))
                .padding(18.dp)
        ) {
            Column {
                Text("Descubre\nnuevos sonidos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Playlists, artistas y más", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Géneros", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            genres.forEach { g ->
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(CardLighter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(g, color = Color.White, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHeader("Tendencias", "Ver todo")
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            trends.forEach { t ->
                Box(
                    Modifier.size(130.dp, 150.dp).clip(RoundedCornerShape(14.dp)).background(t.gradient)
                        .clickable { nav.navigate("playlists") },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(t.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
