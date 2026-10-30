package com.mivehiculo.launcher.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Un único ExoPlayer para toda la app. Vive aunque cierres la pantalla del reproductor, así la música
 * sigue sonando al volver al Home. Se libera solo cuando la pantalla se cierra y no está sonando nada.
 *
 * La MediaSession lo publica al sistema: la tarjeta de música del Home y los botones del volante
 * lo controlan igual que a cualquier otra app de música.
 */
object MusicPlayer {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    /** El reproductor si ya existe (sin crearlo). */
    fun current(): ExoPlayer? = player

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true, // maneja el foco de audio: se pausa solo cuando Jarvis habla o entra una llamada
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.repeatMode = Player.REPEAT_MODE_ALL
        session = try {
            MediaSession.Builder(app, p).build()
        } catch (e: Exception) {
            null // sin sesión el reproductor funciona igual; solo no aparece en la tarjeta del Home
        }
        player = p
        return p
    }

    fun releaseIfIdle() {
        val p = player ?: return
        if (!p.isPlaying) release()
    }

    fun release() {
        try {
            session?.release()
        } catch (e: Exception) {
            // ya estaba liberada
        }
        session = null
        try {
            player?.release()
        } catch (e: Exception) {
            // ya estaba liberado
        }
        player = null
    }
}
