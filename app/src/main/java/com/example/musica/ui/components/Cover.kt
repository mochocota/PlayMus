package com.example.musica.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musica.data.DownloadRepository
import com.example.musica.data.Song
import com.example.musica.ui.theme.GradientBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Caché LRU en memoria: guarda carátulas ya decodificadas.
// Con RGB_565 cada imagen ocupa ~la mitad, así que caben muchas
// más antes de empezar a reciclar (menos re-lecturas de disco).
private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

// Decodifica a un tamaño adaptado a las cuadrículas (~130dp):
// 384px por lado es más que suficiente y muy ligero.
private fun decodeSampled(path: String, target: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target &&
            bounds.outHeight / (sample * 2) >= target
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (_: Throwable) {
        null
    }
}

// Carátula con carga fluida:
// 1) Lectura SINCRÓNICA de la caché: si ya fue decodificada, aparece
//    en el PRIMER frame (sin flash de degradado al reciclar celdas).
// 2) Si no está, se decodifica en segundo plano y se funde suavemente.
// 3) Insignia opcional de "disponible offline".
@Composable
fun CoverImage(
    song: Song?,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    sample: Int = 4,
    offlineBadge: Boolean = false
) {
    val path = song?.coverPath

    // Lectura sincrónica de caché en la primera composición:
    // elimina el flash en celdas recicladas
    var bmp by remember(path) {
        mutableStateOf(if (path.isNullOrBlank()) null else bitmapCache.get(path))
    }

    // Solo si NO estaba en caché: decodifica en segundo plano
    if (bmp == null && !path.isNullOrBlank()) {
        LaunchedEffect(path) {
            val decoded = withContext(Dispatchers.IO) { decodeSampled(path, 384) }
            if (decoded != null) bitmapCache.put(path, decoded)
            bmp = decoded
        }
    }

    Box(modifier) {
        // Fundido suave entre placeholder e imagen (180 ms)
        Crossfade(
            targetState = bmp != null,
            animationSpec = tween(180),
            label = "coverFade"
        ) { hasImage ->
            if (hasImage && bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(corner)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(corner))
                        .background(GradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote, null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        // Insignia: esta canción está descargada (funciona sin internet)
        if (offlineBadge && song != null && DownloadRepository.isDownloaded(song.id)) {
            Icon(
                Icons.Filled.DownloadDone, "Disponible offline",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0891B2))
                    .padding(3.dp)
            )
        }
    }
}
