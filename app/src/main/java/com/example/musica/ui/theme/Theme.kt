package com.example.musica.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MusicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = NavyBg,
            surface = CardDark,
            primary = Purple,
            secondary = Pink
        ),
        content = content
    )
}
