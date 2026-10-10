package com.example.musica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.TextSecondary

@Composable
fun SplashScreen(nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().background(NavyBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1.2f))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)).background(GradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Música", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Tu mundo,\nen una sola app", color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(220.dp).clip(CircleShape)
                .border(3.dp, GradientBrush, CircleShape)
                .background(CardDark),
            contentAlignment = Alignment.Center
        ) {
            Text("🐱", fontSize = 100.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                .background(GradientBrush)
                .clickable { nav.navigate("home") { popUpTo("splash") { inclusive = true } } },
            contentAlignment = Alignment.Center
        ) {
            Text("Comenzar", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(30.dp))
    }
}
