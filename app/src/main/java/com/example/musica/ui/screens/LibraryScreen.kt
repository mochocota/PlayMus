package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.example.musica.data.libraryItems
import com.example.musica.data.recents
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.SectionHeader
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun LibraryScreen(nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().background(NavyBg)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Search, null, tint = Color.White)
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Filled.Settings, null, tint = Color.White,
                modifier = Modifier.clickable { nav.navigate("settings") })
        }
        Spacer(Modifier.height(18.dp))
        libraryItems.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark)
                    .clickable(enabled = item.route != null) { item.route?.let { nav.navigate(it) } }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(CardLighter),
                    contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text(item.label, color = Color.White, modifier = Modifier.weight(1f))
                Text(item.count, color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(16.dp))
        SectionHeader("Recientes", "Ver todo")
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
