package com.example.musica.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

// Caché LRU en memoria (~32 MB) de carátulas decodificadas
private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

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
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (_: Throwable) {
        null
    }
}

// Carátula con carga en segundo plano (scroll fluido) y,
// opcionalmente, insignia de "disponible offline" (offlineBadge).
@Composable
fun CoverImage(
    song: Song?,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    sample: Int = 4,
    offlineBadge: Boolean = false
) {
    val path = song?.coverPath

    val bmp by produceState<Bitmap?>(initialValue = null, path) {
        if (path.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = bitmapCache.get(path)
        if (value != null) return@produceState
        val decoded = withContext(Dispatchers.IO) { decodeSampled(path, 512) }
        if (decoded != null) bitmapCache.put(path, decoded)
        value = decoded
    }

    Box(modifier) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(corner)),
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
                    tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp)
                )
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
