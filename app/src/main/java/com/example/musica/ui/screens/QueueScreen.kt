package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.musica.data.fmtMillis
import com.example.musica.data.fmtSeconds
import com.example.musica.data.queueDemo
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AlbumArt
import com.example.musica.ui.components.Waveform
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.TextSecondary

@Composable
fun QueueScreen(nav: NavHostController, vm: PlayerViewModel) {
    var tab by remember { mutableStateOf(0) }
    val cur = vm.current
    Column(Modifier.fillMaxSize().background(NavyBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White,
                modifier = Modifier.clickable { nav.popBackStack() })
            Text("  En cola", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.MoreVert, null, tint = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            listOf("A continuación", "Historial").forEachIndexed { i, t ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { tab = i }) {
                        Text(t, color = if (tab == i) Color.White else TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(80.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (tab == i) Pink else Color.Transparent))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CardLighter).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Waveform()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cur?.title ?: "Nada sonando", color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(cur?.album ?: "Reproduce una canción", color = TextSecondary,
                    fontSize = 12.sp, maxLines = 1)
            }
            Text(if (vm.duration > 0) fmtMillis(vm.duration) else "0:00",
                color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            queueDemo.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(Modifier.size(46.dp), corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text(s.album, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(fmtSeconds(s.durationSec), color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Filled.BookmarkBorder, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar cola")
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Limpiar")
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(GradientBrush).clickable { },
            contentAlignment = Alignment.Center) {
            Text("Guardar en lista", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
