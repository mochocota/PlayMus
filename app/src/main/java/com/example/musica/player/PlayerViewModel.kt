package com.example.musica.player

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.musica.data.DEMO_URL
import com.example.musica.data.Song
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // Resuelve la URL real de Telegram justo antes de descargar cada canción
    private val player: ExoPlayer = ExoPlayer.Builder(app)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                    ResolvingDataSource.Resolver { dataSpec ->
                        val uri = dataSpec.uri
                        if (uri.host == "tg.file") {
                            val fileId = uri.path?.trimStart('/').orEmpty()
                            val client = TelegramClient(TelegramConfig.token(app))
                            val real = client.fileUrl(client.getFilePath(fileId))
                            dataSpec.buildUpon().setUri(real).build()
                        } else {
                            dataSpec
                        }
                    }
                )
            )
        )
        .build()

    private var currentList: List<Song> = emptyList()

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
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = currentList.getOrNull(player.currentMediaItemIndex)
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(app, "No se pudo reproducir esta pista", Toast.LENGTH_SHORT).show()
            }
        })
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) position = player.currentPosition
                delay(500)
            }
        }
    }

    fun play(song: Song, list: List<Song>) {
        currentList = list
        current = song
        val items = list.map { s ->
            val uri = if (s.fromTelegram && s.fileId.isNotEmpty())
                "https://tg.file/${s.fileId}"
            else s.url.ifBlank { DEMO_URL }
            MediaItem.fromUri(uri)
        }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.setMediaItems(items, index, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = player.seekToNextMediaItem()

    fun prev() = player.seekToPreviousMediaItem()

    fun toggleShuffle() {
        shuffle = !shuffle
        player.shuffleModeEnabled = shuffle
    }

    fun toggleRepeat() {
        repeatOne = !repeatOne
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun seekTo(fraction: Float) {
        if (duration > 0) {
            player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    override fun onCleared() {
        player.release()
    }
}
