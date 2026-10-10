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
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import com.google.android.gms.cast.CastContext
import com.google.android.gms.cast.CastSession
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.QueueData
import com.google.android.gms.cast.RemoteMediaClient
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var currentList: List<Song> = emptyList()
    private var pendingPlay: (() -> Unit)? = null
    private val directExecutor: Executor = Executor { it.run() }

    // ---------- Google Cast (Chromecast / bocinas / TVs compatibles) ----------
    private var castContext: CastContext? = null
    private val castSession: CastSession? get() = castContext?.sessionManager?.currentCastSession
    private val remoteClient: RemoteMediaClient? get() = castSession?.remoteMediaClient
    private var lastCastSongId = -1

    var casting by mutableStateOf(false)
        private set
    var castAvailable by mutableStateOf(true)
        private set

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

        try {
            castContext = CastContext.getSharedInstance(app)
            castAvailable = true
        } catch (_: Exception) {
            castContext = null
            castAvailable = false
        }
        castContext?.sessionManager?.addSessionManagerListener(
            sessionListener, CastSession::class.java
        )

        viewModelScope.launch {
            while (isActive) {
                refreshProgress()
                delay(500)
            }
        }
    }

    // ---------- Sesión Cast ----------
    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) { onCastChanged(true) }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { onCastChanged(true) }
        override fun onSessionEnded(session: CastSession, error: Int) { onCastChanged(false) }
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    }

    private val remoteListener = object : RemoteMediaClient.Listener {
        override fun onStatusUpdated() { refreshProgress() }
        override fun onMetadataUpdated() { refreshProgress() }
        override fun onQueueStatusUpdated() { refreshProgress() }
        override fun onPreloadStatusUpdated() {}
        override fun onSendingRemoteMediaRequest() {}
        override fun onAdBreakStatusUpdated() {}
    }

    private fun onCastChanged(active: Boolean) {
        casting = active
        if (active) {
            controller?.pause()
            lastCastSongId = -1
            remoteClient?.addListener(remoteListener)
            // Transfiere lo que suena a la bocina/TV
            current?.let { sendToCast(it, currentList) }
        } else {
            remoteClient?.removeListener(remoteListener)
            lastCastSongId = -1
            // Al desconectar el cast, retoma en el teléfono
            if (current != null) startPlayback()
        }
    }

    // Envía la cola completa al Chromecast (resuelve las URLs de Telegram)
    private fun sendToCast(song: Song, list: List<Song>) {
        val rc = remoteClient ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val start = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                val limited = list.take(30)
                val items = mutableListOf<MediaQueueItem>()
                for (s in limited) {
                    items.add(MediaQueueItem.Builder(buildCastMediaInfo(s)).build())
                }
                val startIdx = start.coerceAtMost(items.size - 1)
                val queueData = QueueData.Builder()
                    .setItems(items.toTypedArray())
                    .setStartIndex(startIdx)
                    .setRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_OFF)
                    .build()
                val request = MediaLoadRequestData.Builder()
                    .setQueueData(queueData)
                    .setAutoplay(true)
                    .build()
                withContext(Dispatchers.Main) {
                    lastCastSongId = song.id
                    rc.load(request)
                }
            } catch (_: Exception) {}
        }
    }

    // Construye el MediaInfo con la URL pública real (para que la bocina la descargue)
    private fun buildCastMediaInfo(s: Song): MediaInfo {
        val url = if (s.fromTelegram && s.fileId.isNotEmpty()) {
            try {
                val client = TelegramClient(TelegramConfig.token(getApplication()))
                client.fileUrl(client.getFilePath(s.fileId))
            } catch (_: Exception) { DEMO_URL }
        } else {
            s.url.ifBlank { DEMO_URL }
        }
        val md = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        md.putString(CastMediaMetadata.KEY_TITLE, s.title)
        md.putString(CastMediaMetadata.KEY_ARTIST, s.artist)
        if (s.album.isNotBlank()) md.putString(CastMediaMetadata.KEY_ALBUM_TITLE, s.album)
        val custom = JSONObject().put("songId", s.id)
        return MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mpeg")
            .setMetadata(md)
            .setCustomData(custom)
            .build()
    }

    // ---------- Estado unificado (local o cast) ----------
    private fun refreshProgress() {
        if (casting) {
            val rc = remoteClient ?: return
            isPlaying = rc.isPlaying
            val d = rc.streamDuration
            if (d > 0) duration = d
            position = rc.approximateStreamPosition
            val songId = rc.mediaStatus?.mediaInfo?.customData?.optInt("songId", -1) ?: -1
            if (songId > 0 && songId != lastCastSongId) {
                if (lastCastSongId != -1) PlayStatsRepository.increment(songId)
                lastCastSongId = songId
                currentList.firstOrNull { it.id == songId }?.let { current = it }
            }
        } else {
            val c = controller ?: return
            if (c.isPlaying) position = c.currentPosition
            isPlaying = c.isPlaying
        }
    }

    private fun attach(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (!casting) isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !casting) duration = c.duration
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (casting) return
                resolveCurrent(item?.mediaId)
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

        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
        if (c.playbackState == Player.STATE_READY) duration = c.duration
        resolveCurrent(c.currentMediaItem?.mediaId)

        pendingPlay?.invoke()
        pendingPlay = null
    }

    private fun resolveCurrent(mediaId: String?) {
        val id = mediaId?.toIntOrNull()
        current = currentList.firstOrNull { it.id == id }
            ?: MusicRepository.songs.value.firstOrNull { it.id == id }
    }

    // ---------- Reproducción ----------
    fun play(song: Song, list: List<Song>) {
        currentList = list
        current = song
        PlayStatsRepository.increment(song.id)
        if (casting) {
            sendToCast(song, list)
        } else if (controller == null) {
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

    // ---------- Controles (redirigen al cast o al local) ----------
    fun toggle() {
        if (casting) {
            val rc = remoteClient ?: return
            if (rc.isPlaying) rc.pause() else rc.play()
        } else {
            val c = controller ?: return
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun next() {
        if (casting) remoteClient?.queueNext(null)
        else controller?.seekToNextMediaItem()
    }

    fun prev() {
        if (casting) remoteClient?.queuePrev(null)
        else controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        shuffle = !shuffle
        c.shuffleModeEnabled = shuffle
    }

    fun toggleRepeat() {
        repeatOne = !repeatOne
        if (casting) {
            remoteClient?.queueSetRepeatMode(
                if (repeatOne) MediaStatus.REPEAT_MODE_REPEAT_ONE
                else MediaStatus.REPEAT_MODE_REPEAT_OFF,
                null
            )
        } else {
            controller?.repeatMode =
                if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    fun seekTo(fraction: Float) {
        if (duration <= 0) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        if (casting) remoteClient?.seek(target)
        else controller?.seekTo(target)
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
    }
}
