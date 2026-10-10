package com.example.musica.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musica.data.DEMO_URL
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var currentList: List<Song> = emptyList()
    private var pendingPlay: (() -> Unit)? = null
    private val directExecutor: Executor = Executor { it.run() }

    var current: Song? by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var position by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatOne by mutableStateOf(false)
        private set

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            try {
                val c = controllerFuture.get()
                controller = c
                attach(c)
            } catch (_: Exception) {}
        }, directExecutor)

        // Actualiza la posición cada medio segundo
        viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) position = c.currentPosition
                delay(500)
            }
        }
    }

    private fun attach(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = c.duration
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                resolveCurrent(item?.mediaId)
                // Cuenta reproducción: paso automático (terminó) o salto manual
                if (current != null &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
                ) {
                    current?.let { PlayStatsRepository.increment(it.id) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(getApplication(), "No se pudo reproducir esta pista",
                    Toast.LENGTH_SHORT).show()
            }
        })

        // Sincroniza estado por si el servicio ya estaba reproduciendo
        // (por ejemplo, al volver a abrir la app)
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
        if (c.playbackState == Player.STATE_READY) duration = c.duration
        resolveCurrent(c.currentMediaItem?.mediaId)

        // Ejecuta una reproducción que llegó antes de conectar
        pendingPlay?.invoke()
        pendingPlay = null
    }

    private fun resolveCurrent(mediaId: String?) {
        val id = mediaId?.toIntOrNull()
        current = currentList.firstOrNull { it.id == id }
            ?: MusicRepository.songs.value.firstOrNull { it.id == id }
    }

    fun play(song: Song, list: List<Song>) {
        currentList = list
        current = song
        PlayStatsRepository.increment(song.id)
        if (controller == null) {
            pendingPlay = { startPlayback() }
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val c = controller ?: return
        val items = currentList.map { toMediaItem(it) }
        val index = currentList.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    // Convierte una Song en MediaItem con metadatos para la notificación
    private fun toMediaItem(s: Song): MediaItem {
        val uri = if (s.fromTelegram && s.fileId.isNotEmpty())
            "https://tg.file/${s.fileId}"
        else s.url.ifBlank { DEMO_URL }
        val artwork = if (s.coverPath.isNotBlank()) Uri.fromFile(File(s.coverPath)) else null
        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setAlbumTitle(s.album.ifBlank { null })
            .setArtworkUri(artwork)
            .build()
        return MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun prev() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        shuffle = !shuffle
        c.shuffleModeEnabled = shuffle
    }

    fun toggleRepeat() {
        val c = controller ?: return
        repeatOne = !repeatOne
        c.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun seekTo(fraction: Float) {
        val c = controller ?: return
        if (duration > 0) {
            c.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
    }
}
