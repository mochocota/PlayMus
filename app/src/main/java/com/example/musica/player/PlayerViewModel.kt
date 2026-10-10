package com.example.musica.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.musica.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player = ExoPlayer.Builder(app).build()
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
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.setMediaItems(list.map { MediaItem.fromUri(it.url) }, index, 0L)
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
