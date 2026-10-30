package com.mivehiculo.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mivehiculo.launcher.MediaListenerService

/** Lo que muestra la tarjeta de música. Todo tiene valor por defecto: "sin música" es un estado normal. */
data class MediaState(
    val hasAccess: Boolean = true,
    val hasSession: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val stateTimeMs: Long = 0L,
    val speed: Float = 1f,
) {
    /** Posición actual estimada; la barra avanza sola entre avisos de la app de música. */
    fun positionAt(now: Long): Long {
        if (!playing) return positionMs
        val p = positionMs + ((now - stateTimeMs) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }
}

/**
 * Escucha la sesión de medios activa (la app que suena) y le envía comandos.
 * Se activa con start() y se desactiva con stop(): cuando el launcher no se ve, no consume nada.
 * Ningún método lanza excepciones hacia fuera.
 */
class MediaWatcher(private val context: Context, private val onChange: (MediaState) -> Unit) {

    private val component = ComponentName(context, MediaListenerService::class.java)
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private var controller: MediaController? = null
    private var registered = false
    private var lastArtSource: Bitmap? = null
    private var lastArt: ImageBitmap? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    /** ¿Está activado "Acceso a notificaciones" para esta app? */
    fun hasAccess(): Boolean = try {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    } catch (e: Exception) {
        false
    }

    fun start() {
        val m = manager
        if (m == null || !hasAccess()) {
            onChange(MediaState(hasAccess = false))
            return
        }
        try {
            if (!registered) {
                m.addOnActiveSessionsChangedListener(sessionsListener, component)
                registered = true
            }
            pick(m.getActiveSessions(component))
        } catch (e: Exception) {
            // SecurityException si el permiso se revocó justo ahora, o el servicio aún no está enlazado.
            onChange(MediaState(hasAccess = false))
        }
    }

    fun stop() {
        try {
            if (registered) manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // nada que hacer
        }
        registered = false
        unbind()
    }

    /* ---------- Controles: no hacen nada si no hay sesión ---------- */

    fun playPause() = control {
        if (it.playbackState?.state == PlaybackState.STATE_PLAYING) it.transportControls.pause()
        else it.transportControls.play()
    }

    fun next() = control { it.transportControls.skipToNext() }

    fun previous() = control { it.transportControls.skipToPrevious() }

    private fun control(block: (MediaController) -> Unit) {
        try {
            controller?.let(block)
        } catch (e: Exception) {
            // la app de música cerró su sesión justo en ese instante: se ignora
        }
    }

    /* ---------- Internos ---------- */

    private fun refresh() {
        try {
            if (hasAccess()) pick(manager?.getActiveSessions(component)) else publish()
        } catch (e: Exception) {
            unbind()
            onChange(MediaState(hasAccess = hasAccess()))
        }
    }

    /** Elige la sesión que está sonando; si ninguna suena, la más reciente. */
    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            unbind()
            controller = best
            try {
                best?.registerCallback(callback)
            } catch (e: Exception) {
                controller = null
            }
        }
        publish()
    }

    private fun unbind() {
        try {
            controller?.unregisterCallback(callback)
        } catch (e: Exception) {
            // ya estaba desregistrado
        }
        controller = null
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            onChange(MediaState(hasAccess = true))
            return
        }
        try {
            val md = c.metadata
            val st = c.playbackState
            val position = st?.position ?: 0L
            onChange(
                MediaState(
                    hasAccess = true,
                    hasSession = true,
                    title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                    artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.takeIf { it.isNotBlank() },
                    art = artOf(md),
                    playing = st?.state == PlaybackState.STATE_PLAYING,
                    durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                    positionMs = if (position < 0L) 0L else position,
                    stateTimeMs = st?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime(),
                    speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                )
            )
        } catch (e: Exception) {
            onChange(MediaState(hasAccess = true))
        }
    }

    /** Carátula en pequeño (máx. 320 px, ~400 KB) y copiada: nunca depende del bitmap de la otra app. */
    private fun artOf(md: MediaMetadata?): ImageBitmap? {
        val src = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (src == null || src.isRecycled) {
            lastArtSource = null
            lastArt = null
            return null
        }
        if (src === lastArtSource) return lastArt
        val img = try {
            val max = maxOf(src.width, src.height)
            val own = if (max > 320) {
                val f = 320f / max
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * f).toInt().coerceAtLeast(1),
                    (src.height * f).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                src.copy(Bitmap.Config.ARGB_8888, false)
            }
            own?.asImageBitmap()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        lastArtSource = src
        lastArt = img
        return img
    }
}
