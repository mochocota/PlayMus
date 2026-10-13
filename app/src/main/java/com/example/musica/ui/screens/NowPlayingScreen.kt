package com.example.musica.ui.screens

import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.example.musica.audio.EqualizerManager
import com.example.musica.data.DownloadRepository
import com.example.musica.data.FavoritesRepository
import com.example.musica.data.LyricLine
import com.example.musica.data.LyricsRepository
import com.example.musica.data.LyricsResult
import com.example.musica.data.MusicRepository
import com.example.musica.data.Song
import com.example.musica.data.fmtMillis
import com.example.musica.player.PlayerViewModel
import com.example.musica.ui.components.AddToPlaylistDialog
import com.example.musica.ui.components.CoverImage
import com.example.musica.ui.theme.CardDark
import com.example.musica.ui.theme.CardLighter
import com.example.musica.ui.theme.Cyan
import com.example.musica.ui.theme.GradientBrush
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Pink
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import com.example.musica.ui.theme.TrackColor
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(nav: NavHostController, vm: PlayerViewModel) {
    val song = vm.current
    val favIds by FavoritesRepository.ids.collectAsState()
    val liked = song != null && song.id in favIds
    var showFavAnim by remember { mutableStateOf(false) }
    var favMsg by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showDeleteDl by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val dlVersion by DownloadRepository.version.collectAsState()
    val dlProgress by DownloadRepository.progress.collectAsState()
    val context = LocalContext.current

    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LyricsResult?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsFail by remember { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<Int?>(null) }
    var retry by remember { mutableStateOf(0) }

    val castUsable = remember {
        try {
            CastContext.getSharedInstance(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    LaunchedEffect(showFavAnim) {
        if (showFavAnim) {
            delay(1300)
            showFavAnim = false
        }
    }

    LaunchedEffect(showLyrics, song?.id, retry) {
        val s = song ?: return@LaunchedEffect
        if (showLyrics && loadedFor != s.id) {
            lyricsLoading = true
            lyricsFail = false
            val res = try { LyricsRepository.getFor(s) } catch (_: Exception) { null }
            lyrics = res
            lyricsFail = res == null
            loadedFor = s.id
            lyricsLoading = false
        }
    }

    val isDownloaded = remember(dlVersion, song?.id) {
        song != null && DownloadRepository.isDownloaded(song.id)
    }
    val dl = song?.let { dlProgress[it.id] }

    Box(Modifier.fillMaxSize().background(NavyBg)) {

        if (showLyrics && song != null) {
            LyricsView(
                vm = vm, song = song,
                lyrics = lyrics, loading = lyricsLoading, failed = lyricsFail,
                onClose = { showLyrics = false },
                onRetry = { loadedFor = null; retry++ }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

                Spacer(Modifier.height(12.dp))

                // ---------- Encabezado con subtítulo ----------
                Text("Reproduciendo Ahora", color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    song?.album?.ifBlank { song.genre }?.ifBlank { "Melodix" } ?: "Melodix",
                    color = TextSecondary, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                if (song == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Elige una canción para empezar", color = TextSecondary)
                    }
                } else {
                    // Espacio flexible ARRIBA (igual que abajo)
                    Spacer(Modifier.weight(1f))

                    // ---------- Bloque central ----------
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

                        // Carátula con Cast overlay
                        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song
                            CoverImage(fresh, Modifier.fillMaxWidth().aspectRatio(1f),
                                corner = 14.dp, sample = 1)

                            // Cast dentro de la carátula, arriba a la derecha
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (castUsable) {
                                    AndroidView(
                                        modifier = Modifier.size(26.dp),
                                        factory = { ctx ->
                                            try {
                                                val themed = ContextThemeWrapper(
                                                    ctx,
                                                    androidx.appcompat.R.style.Theme_AppCompat_NoActionBar
                                                )
                                                MediaRouteButton(themed).apply {
                                                    CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                                    visibility = android.view.View.VISIBLE
                                                }
                                            } catch (_: Exception) {
                                                android.view.View(ctx)
                                            }
                                        }
                                    )
                                } else {
                                    IconButton(onClick = {
                                        Toast.makeText(context,
                                            "Cast no disponible en este dispositivo",
                                            Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.Cast, "Cast", tint = Color.White,
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ---------- Título + Artista | Letra + Corazón ----------
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 26.sp)
                                Text(song.artist, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
                            }

                            // Letra (pill con borde)
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable { showLyrics = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lyrics, "Ver letra", tint = Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(8.dp))

                            // Corazón (pill con borde)
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        val nowFav = FavoritesRepository.toggle(song.id)
                                        favMsg = if (nowFav) "Agregado a favoritos"
                                        else "Eliminado de favoritos"
                                        showFavAnim = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    null, tint = if (liked) Pink else Color.White,
                                    modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // ---------- Línea de tiempo ----------
                        val rawProgress = if (dragging) dragValue
                        else if (vm.duration > 0) vm.position.toFloat() / vm.duration else 0f
                        val progress = if (rawProgress.isNaN() || rawProgress < 0f) 0f
                        else rawProgress.coerceAtMost(1f)

                        Slider(
                            value = progress,
                            onValueChange = { dragging = true; dragValue = it },
                            onValueChangeFinished = { vm.seekTo(dragValue); dragging = false },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = TrackColor
                            )
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmtMillis(vm.position), color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(fmtMillis(vm.duration), color = TextSecondary, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(6.dp))

                        // ---------- Controles: ⏮ Reproducir ⏭ ----------
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {

                            // Anterior (círculo oscuro)
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CardLighter)
                                    .clickable { vm.prev() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.SkipPrevious, "Anterior",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            // Reproducir / Pausar (degradado de siempre)
                            Box(
                                Modifier
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(GradientBrush)
                                    .clickable { vm.toggle() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                ) {
                                    Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (vm.isPlaying) "Pausar" else "Reproducir",
                                        color = Color.White, fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Siguiente (círculo oscuro)
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CardLighter)
                                    .clickable { vm.next() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.SkipNext, "Siguiente",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    // Espacio flexible ABAJO (igual que arriba)
                    Spacer(Modifier.weight(1f))

                    // ---------- Fila inferior: Sleep EQ Shuffle Repeat Cola · Descarga ----------
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {

                        Row(Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {

                            // Sleep
                            BottomButton(Icons.Filled.NightsStay,
                                active = vm.sleepTimerRemaining > 0 || vm.sleepTimerEndOfSong
                            ) { showSleep = true }

                            // Ecualizador
                            BottomButton(Icons.Filled.Equalizer,
                                active = false) { showEq = true }

                            // Aleatorio (Shuffle)
                            BottomButton(Icons.Filled.Shuffle,
                                active = vm.shuffle) { vm.toggleShuffle() }

                            // Repetir
                            BottomButton(Icons.Filled.Repeat,
                                active = vm.repeatOne) { vm.toggleRepeat() }

                            // Cola
                            BottomButton(Icons.AutoMirrored.Filled.QueueMusic,
                                active = false) { nav.navigate("queue") }
                        }

                        // ---------- Descarga (con estados visibles) ----------
                        Box(modifier = Modifier) {
                            IconButton(onClick = {
                                when {
                                    dl != null -> { /* descargando: sin acción */ }
                                    isDownloaded -> showDeleteDl = true
                                    else -> DownloadRepository.startDownload(song) { result ->
                                        Toast.makeText(
                                            context,
                                            if (result == "ok") "Descarga completa ✓" else result,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }) {
                                when {
                                    dl != null -> {
                                        val frac = if (dl.second > 0)
                                            dl.first.toFloat() / dl.second else 0f
                                        CircularProgressIndicator(
                                            progress = { frac.coerceIn(0f, 1f) },
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = Cyan
                                        )
                                    }
                                    isDownloaded -> Box(
                                        Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Cyan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.DownloadDone, "Descargada",
                                            tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    else -> Icon(Icons.Filled.Download, "Descargar",
                                        tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        // ---------- Animación de favoritos ----------
        AnimatedVisibility(
            visible = showFavAnim && song != null && !showLyrics,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.5f, animationSpec = tween(250)) +
                    fadeIn(animationSpec = tween(250)),
            exit = scaleOut(targetScale = 1.35f, animationSpec = tween(350)) +
                    fadeOut(animationSpec = tween(350))
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp)).background(CardDark)
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (favMsg == "Agregado a favoritos") Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (favMsg == "Agregado a favoritos") Pink else TextSecondary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(favMsg, color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    AddToPlaylistDialog(songToAdd) { songToAdd = null }

    if (showEq) EqualizerDialog(onDismiss = { showEq = false })

    if (showSleep) SleepTimerDialog(vm) { showSleep = false }

    if (showDeleteDl && song != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDl = false },
            containerColor = CardDark,
            title = { Text("Eliminar descarga", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Eliminar «${song.title}» del teléfono? Podrás volver a escucharla en línea.",
                    color = TextSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadRepository.delete(song.id)
                    showDeleteDl = false
                }) { Text("Eliminar", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDl = false }) { Text("Cancelar", color = Purple) }
            }
        )
    }
}

// ============================================================
// Botón de la fila inferior (cuadrado redondeado con borde;
// activo = fondo blanco con ícono negro)
// ============================================================
@Composable
private fun BottomButton(icon: androidx.compose.ui.graphics.vector.ImageVector,
                         active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White else Color.Transparent)
            .border(
                1.dp,
                if (active) Color.White else TextSecondary.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null,
            tint = if (active) Color.Black else Color.White,
            modifier = Modifier.size(22.dp))
    }
}

// ============================================================
// Diálogo del ecualizador
// ============================================================
@Composable
private fun EqualizerDialog(onDismiss: () -> Unit) {
    val available = EqualizerManager.available
    val bandCount = if (available) EqualizerManager.bandCount().coerceAtLeast(1) else 5
    val range = EqualizerManager.bandRange()

    var enabled by remember { mutableStateOf(EqualizerManager.isEnabled()) }
    var preset by remember { mutableStateOf(EqualizerManager.currentPreset()) }
    var levels by remember {
        mutableStateOf((0 until bandCount).map { EqualizerManager.bandLevel(it) })
    }
    var bass by remember { mutableStateOf(EqualizerManager.bassStrength()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ecualizador", color = Color.White,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; EqualizerManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Purple)
                )
            }
        },
        text = {
            if (!available) {
                Text(
                    "El ecualizador no está disponible en este dispositivo (o aún no hay sesión de audio activa).\n\nReproduce una canción y abre el ecualizador de nuevo.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {

                    Text("Presets", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        (0 until EqualizerManager.presetCount()).forEach { i ->
                            val selected = preset == i
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Purple else CardLighter)
                                    .clickable {
                                        EqualizerManager.usePreset(i)
                                        preset = i
                                        levels = (0 until bandCount)
                                            .map { EqualizerManager.bandLevel(it) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(EqualizerManager.presetName(i),
                                    color = Color.White, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    levels.forEachIndexed { i, lvl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatFreq(EqualizerManager.bandFreq(i)),
                                color = TextSecondary, fontSize = 11.sp,
                                modifier = Modifier.width(52.dp))
                            Slider(
                                value = lvl.toFloat(),
                                onValueChange = { v ->
                                    val nv = v.toInt()
                                    levels = levels.toMutableList().also { l -> l[i] = nv }
                                    preset = -1
                                    EqualizerManager.setBandLevel(i, nv)
                                },
                                valueRange = range[0].toFloat()..range[1].toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Purple,
                                    inactiveTrackColor = TrackColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(dbLabel(lvl), color = TextSecondary, fontSize = 11.sp,
                                modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bass", color = TextSecondary, fontSize = 11.sp,
                            modifier = Modifier.width(52.dp))
                        Slider(
                            value = bass.toFloat(),
                            onValueChange = { v ->
                                bass = v.toInt()
                                EqualizerManager.setBassStrength(v.toInt())
                            },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Pink,
                                inactiveTrackColor = TrackColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(bass * 100) / 1000}%",
                            color = TextSecondary, fontSize = 11.sp,
                            modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Purple) }
        }
    )
}

private fun formatFreq(milliHz: Int): String =
    if (milliHz >= 1_000_000) "%.1fk".format(milliHz / 1_000_000f)
    else "${milliHz / 1000}"

private fun dbLabel(milliBel: Int): String {
    val db = milliBel / 100
    return if (db > 0) "+$db" else "$db"
}

// ============================================================
// Diálogo del Sleep Timer
// ============================================================
@Composable
private fun SleepTimerDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val active = vm.sleepTimerRemaining > 0 || vm.sleepTimerEndOfSong
    val remaining = vm.sleepTimerRemaining

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Temporizador de sueño", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                if (active) {
                    if (vm.sleepTimerEndOfSong) {
                        Text("🌙 Se detendrá al final de esta canción",
                            color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        val min = remaining / 60000
                        val sec = (remaining % 60000) / 1000
                        Text("%d:%02d".format(min, sec),
                            color = Cyan, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text("La música se pausará al terminar",
                            color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = {
                        vm.cancelSleepTimer()
                        onDismiss()
                    }) { Text("Cancelar temporizador", color = Pink) }
                } else {
                    Text("La música se pausará cuando termine el tiempo:",
                        color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        SleepChip("15 min", Modifier.weight(1f)) {
                            vm.startSleepTimer(15); onDismiss()
                        }
                        SleepChip("30 min", Modifier.weight(1f)) {
                            vm.startSleepTimer(30); onDismiss()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        SleepChip("45 min", Modifier.weight(1f)) {
                            vm.startSleepTimer(45); onDismiss()
                        }
                        SleepChip("1 hora", Modifier.weight(1f)) {
                            vm.startSleepTimer(60); onDismiss()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        SleepChip("2 horas", Modifier.weight(1f)) {
                            vm.startSleepTimer(120); onDismiss()
                        }
                        SleepChip("Final can.", Modifier.weight(1f)) {
                            vm.sleepAtEndOfSong(); onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Purple) }
        }
    )
}

@Composable
private fun SleepChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardLighter)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================
// Vista de letra (con traducción opcional)
// ============================================================
@Composable
private fun LyricsView(
    vm: PlayerViewModel,
    song: Song,
    lyrics: LyricsResult?,
    loading: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val fresh = MusicRepository.songs.value.firstOrNull { it.id == song.id } ?: song
    val scope = rememberCoroutineScope()

    var translations by remember { mutableStateOf<List<String>?>(null) }
    var plainTranslation by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var transMsg by remember { mutableStateOf("") }

    fun doTranslate() {
        if (translating) return
        translating = true
        transMsg = ""
        scope.launch {
            val syncedL = lyrics?.synced.orEmpty()
            if (syncedL.isNotEmpty()) {
                val res = try {
                    LyricsRepository.getTranslation(song.id, syncedL.map { it.text })
                } catch (_: Exception) { null }
                if (res == null) {
                    transMsg = "No se pudo traducir. Inténtalo de nuevo."
                } else if (res.lang.startsWith("es")) {
                    transMsg = "La letra ya está en español ✓"
                    translations = null
                    showTranslation = false
                } else {
                    translations = res.lines
                    showTranslation = true
                    transMsg = "Traducido desde ${res.lang.uppercase()} ✓"
                }
            } else if (lyrics?.plain != null) {
                val res = try { LyricsRepository.translateText(lyrics?.plain ?: "") }
                catch (_: Exception) { null }
                if (res == null) {
                    transMsg = "No se pudo traducir. Inténtalo de nuevo."
                } else if (res.first.startsWith("es")) {
                    transMsg = "La letra ya está en español ✓"
                    plainTranslation = null
                    showTranslation = false
                } else {
                    plainTranslation = res.second
                    showTranslation = true
                    transMsg = "Traducido desde ${res.first.uppercase()} ✓"
                }
            }
            translating = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, null, tint = Color.White)
            }
            Text("Letra", color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center)

            IconButton(onClick = {
                if (translations != null || plainTranslation != null) {
                    showTranslation = !showTranslation
                    transMsg = ""
                } else {
                    doTranslate()
                }
            }) {
                if (translating) {
                    CircularProgressIndicator(Modifier.size(20.dp),
                        strokeWidth = 2.dp, color = Cyan)
                } else {
                    Icon(Icons.Filled.Translate, "Traducir",
                        tint = if (showTranslation) Cyan else Color.White,
                        modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Letra por LRCLIB · Traducción por Google",
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 10.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))

        if (transMsg.isNotBlank()) {
            Text(transMsg,
                color = if (transMsg.startsWith("No se")) Pink else Cyan,
                fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Purple)
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando letra…", color = TextSecondary, fontSize = 14.sp)
                    }
                }
                failed -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("♪", color = TextSecondary, fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No se encontró la letra de esta canción",
                            color = TextSecondary, fontSize = 14.sp,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Reintentar", color = Purple, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                else -> {
                    val synced = lyrics?.synced.orEmpty()
                    val plain = lyrics?.plain
                    if (synced.isNotEmpty()) {
                        SyncedLyrics(synced, vm, translations, showTranslation)
                    } else if (plain != null) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(vertical = 20.dp)
                        ) {
                            Text(plain, color = Color.White, fontSize = 16.sp,
                                lineHeight = 26.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                            if (showTranslation && plainTranslation != null) {
                                Spacer(Modifier.height(20.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp)
                                    .background(TextSecondary.copy(alpha = 0.3f)))
                                Spacer(Modifier.height(20.dp))
                                Text(plainTranslation ?: "", color = TextSecondary,
                                    fontSize = 14.sp, lineHeight = 22.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Text("No se encontró la letra de esta canción",
                            color = TextSecondary, fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CardLighter).clickable(onClick = onClose).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CoverImage(fresh, Modifier.size(40.dp), corner = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Filled.KeyboardArrowUp, "Volver al reproductor", tint = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
    }
}

// Letra sincronizada bilingüe
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    vm: PlayerViewModel,
    translations: List<String>?,
    showTranslation: Boolean
) {
    val listState = rememberLazyListState()
    var current by remember { mutableStateOf(-1) }

    LaunchedEffect(vm.position, lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        val idx = lines.indexOfLast { it.timeMs <= vm.position }
        if (idx != current) {
            current = idx
            if (idx >= 0) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(lines.size) { i ->
            val active = i == current
            val tr = if (showTranslation && translations != null && i < translations.size)
                translations[i].trim() else ""

            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = vm.duration > 0) {
                        vm.seekTo(lines[i].timeMs.toFloat() / vm.duration)
                    }
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = lines[i].text.ifBlank { "♪" },
                    color = if (active) Color.White else TextSecondary.copy(alpha = 0.75f),
                    fontSize = if (active) 19.sp else 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = if (active) 26.sp else 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tr.isNotBlank()) {
                    Text(
                        text = tr,
                        color = if (active) Color.White.copy(alpha = 0.9f)
                        else TextSecondary.copy(alpha = 0.55f),
                        fontSize = if (active) 14.sp else 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
