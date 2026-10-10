package com.example.musica.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musica.telegram.TelegramClient
import com.example.musica.telegram.TelegramConfig

// Servicio que mantiene la música viva en segundo plano y genera
// automáticamente la notificación con carátula y controles.
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ResolvingDataSource.Factory(
                        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                        ResolvingDataSource.Resolver { dataSpec ->
                            val uri = dataSpec.uri
                            if (uri.host == "tg.file") {
                                val fileId = uri.path?.trimStart('/').orEmpty()
                                val client = TelegramClient(TelegramConfig.token(this))
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

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // Si el usuario cierra la app de la lista de recientes:
    // sigue sonando si hay reproducción activa; si estaba pausada, se detiene.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady ||
            player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
