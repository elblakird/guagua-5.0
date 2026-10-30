package com.mivehiculo.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar

class VolumeOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var overlayView: View? = null
    private var progressBar: ProgressBar? = null
    private var handler: Handler? = null
    private lateinit var volumeObserver: ContentObserver
    private val hideRunnable = Runnable { hideOverlay() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        handler = Handler(Looper.getMainLooper())
        
        createOverlayView()
        setupVolumeObserver()
    }

    private fun createOverlayView() {
        // Contenedor principal (Fondo oscuro semitransparente)
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(60, 60, 60, 60)
        container.setBackgroundColor(Color.parseColor("#CC1E1E1E")) // Negro con transparencia

        // Barra de progreso (Color naranja para mantener el estilo OEM+)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar?.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        progressBar?.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8C00"))

        container.addView(progressBar)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM
        params.y = 150 // Margen desde abajo

        overlayView = container
        windowManager.addView(overlayView, params)
        overlayView?.visibility = View.GONE // Oculto por defecto
    }

    private fun setupVolumeObserver() {
        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                showOverlay()
            }
        }
        contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
    }

    private fun showOverlay() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        progressBar?.progress = currentVolume
        overlayView?.visibility = View.VISIBLE
        
        // Ocultar después de 2 segundos
        handler?.removeCallbacks(hideRunnable)
        handler?.postDelayed(hideRunnable, 2000)
    }

    private fun hideOverlay() {
        overlayView?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(hideRunnable)
        try {
            contentResolver.unregisterContentObserver(volumeObserver)
        } catch (e: Exception) {}
        
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
