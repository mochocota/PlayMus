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

// Servicio que mantiene la música viva en segundo plano y genera
// automáticamente la notificación con carátula y controles.
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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
                        // file:// (descargas offline), https:// (streaming),
                        // content://, etc. Antes solo hablaba HTTP y por
                        // eso los archivos locales fallaban.
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

        // Asigna la sesión de audio AL REPRODUCTOR YA CONSTRUIDO
        // (por eso el ecualizador puede engancharse a ella)
        if (sessionId > 0) {
            try { player.setAudioSessionId(sessionId) } catch (_: Exception) {}
        }

        // Engancha el ecualizador (restaura tus ajustes guardados)
        if (sessionId > 0) EqualizerManager.attach(sessionId)

        mediaSession = MediaSession.Builder(this, player).build()
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
        EqualizerManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
