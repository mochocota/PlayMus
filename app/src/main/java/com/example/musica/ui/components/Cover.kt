package com.example.musica.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.musica.data.Song
import com.example.musica.ui.theme.GradientBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================
// Caché LRU en memoria (~32 MB): guarda las carátulas ya
// decodificadas. Al desplazarse, las visibles recientemente se
// muestran al instante sin releer el disco. Es thread-safe.
// ============================================================
private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

// Decodifica la imagen en un tamaño razonable (máx ~512px por lado):
// suficiente nitidez y un consumo de memoria bajo para las cuadrículas
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

// Muestra la carátula guardada; si no hay, un degradado con nota.
// La decodificación ocurre en SEGUNDO PLANO (Dispatchers.IO): el
// desplazamiento en cuadrículas ya no se congela ni da tirones.
@Composable
fun CoverImage(song: Song?, modifier: Modifier = Modifier, corner: Dp = 14.dp, sample: Int = 4) {
    val path = song?.coverPath

    val bmp by produceState<Bitmap?>(initialValue = null, path) {
        if (path.isNullOrBlank()) {
            value = null
            return@produceState
        }
        // 1º: ¿está en la caché? → instantáneo
        value = bitmapCache.get(path)
        if (value != null) return@produceState
        // 2º: decodifica en segundo plano y guarda en caché
        val decoded = withContext(Dispatchers.IO) { decodeSampled(path, 512) }
        if (decoded != null) bitmapCache.put(path, decoded)
        value = decoded
    }

    if (bmp != null) {
        Image(
            bitmap = bmp!!.asImageBitmap(),
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
