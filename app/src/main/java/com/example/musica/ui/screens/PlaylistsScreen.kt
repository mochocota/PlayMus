package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.data.playlists
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.TextSecondary

@Composable
fun PlaylistsScreen(nav: NavHostController) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  Listas de reproducción", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Add, null, tint = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            listOf("Tus listas", "Recomendadas").forEachIndexed { i, t ->
                Column(Modifier.clickable { tab = i }.padding(end = 22.dp)) {
                    Text(t, color = if (tab == i) Color.White else TextSecondary,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.width(90.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (tab == i) Pink else Color.Transparent)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(playlists.size) { i ->
                val p = playlists[i]
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("playlist/${p.id}") }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(p.color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = p.color)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 15.sp)
                        Text("${p.count} canciones", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
