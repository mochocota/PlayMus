package com.example.musica.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musica.audio.EqualizerManager
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Servicio que mantiene la música viva en segundo plano y genera
// automáticamente la notificación con carátula y controles.
// Además pre-resuelve la siguiente canción de la cola para que
// las transiciones sean instantáneas.
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        EqualizerManager.init(this)

        // Genera un ID de sesión de audio propio para poder
        // enganchar el ecualizador al reproductor
        val sessionId = try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .generateAudioSessionId()
        } catch (_: Exception) { 0 }

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ResolvingDataSource.Factory(
                        // DefaultDataSource entiende TODOS los esquemas:
                        // file:// (descargas offline) y https:// (streaming)
                        DefaultDataSource.Factory(
                            this,
                            DefaultHttpDataSource.Factory()
                                .setAllowCrossProtocolRedirects(true)
                        ),
                        ResolvingDataSource.Resolver { dataSpec ->
                            val uri = dataSpec.uri
                            if (uri.host == "tg.file") {
                                val fileId = uri.path?.trimStart('/').orEmpty()
                                val client = TelegramClient(TelegramConfig.token(this))
                                val real = client.resolveFileUrl(fileId)
                                dataSpec.buildUpon().setUri(real).build()
                            } else {
                                dataSpec
                            }
                        }
                    )
                )
            )
            .build()

        // Asigna la sesión de audio AL REPRODUCTOR YA CONSTRUIDO
        if (sessionId > 0) {
            try { player.setAudioSessionId(sessionId) } catch (_: Exception) {}
        }

        // Engancha el ecualizador (restaura tus ajustes guardados)
        if (sessionId > 0) EqualizerManager.attach(sessionId)

        // PRE-RESOLUCIÓN: cuando arranca una canción (o pasa a la
        // siguiente), resuelve ya la URL de la siguiente pista en
        // segundo plano; queda en la caché y la transición es instantánea
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) preloadNext(player)
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                preloadNext(player)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun preloadNext(player: Player) {
        try {
            val idx = player.currentMediaItemIndex + 1
            if (idx >= player.mediaItemCount) return
            val uri = player.getMediaItemAt(idx).localConfiguration?.uri ?: return
            if (uri.host != "tg.file") return
            val fileId = uri.path?.trimStart('/').orEmpty()
            if (fileId.isEmpty()) return
            serviceScope.launch {
                try {
                    TelegramClient(TelegramConfig.token(this@PlaybackService))
                        .resolveFileUrl(fileId)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady ||
            player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        EqualizerManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
