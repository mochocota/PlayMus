package com.example.musica.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musica.data.Song
import com.example.musica.ui.theme.GradientBrush
import java.io.File

// Muestra la carátula guardada; si no hay, muestra un degradado con nota musical
@Composable
fun CoverImage(song: Song?, modifier: Modifier = Modifier, corner: Dp = 14.dp, sample: Int = 4) {
    val path = song?.coverPath
    val bmp = remember(song?.id, path) {
        path?.takeIf { it.isNotBlank() }?.let { p ->
            val f = File(p)
            if (f.exists()) try {
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(p, opts)
            } catch (_: Exception) { null } else null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier.clip(RoundedCornerShape(corner)).background(GradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp)
            )
        }
    }
}
