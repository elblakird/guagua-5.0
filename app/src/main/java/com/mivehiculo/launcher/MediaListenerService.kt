package com.mivehiculo.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mivehiculo.launcher.data.MediaState

class MediaListenerService : NotificationListenerService() {

    companion object {
        var mediaState by mutableStateOf(MediaState())
        var mediaController: MediaController? = null

        fun playPause() {
            try {
                // Si el estado es PLAYING, pausamos, si no, le damos play
                if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    mediaController?.transportControls?.pause()
                } else {
                    mediaController?.transportControls?.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun next() { mediaController?.transportControls?.skipToNext() }
        fun prev() { mediaController?.transportControls?.skipToPrevious() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateMediaController()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.packageName != packageName) {
            updateMediaController()
        }
    }

    private fun updateMediaController() {
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaListenerService::class.java)
        
        try {
            val controllers = manager.getActiveSessions(componentName)
            if (controllers.isNotEmpty()) {
                mediaController = controllers.first()
                mediaController?.registerCallback(object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        super.onMetadataChanged(metadata)
                        actualizarEstado(metadata)
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        super.onPlaybackStateChanged(state)
                        actualizarEstado(mediaController?.metadata)
                    }
                })
                actualizarEstado(mediaController?.metadata)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun actualizarEstado(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val playing = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
        
        // Actualizamos el estado global para que la pantalla se entere
        mediaState = MediaState(
            title = title,
            artist = artist,
            playing = playing,
            hasSession = true
        )
    }
}
