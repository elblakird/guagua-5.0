package com.mivehiculo.launcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar

class VolumeOverlayService : Service() {

    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager
    private lateinit var volumeProgress: ProgressBar
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideRunnable = Runnable {
        if (::volumeProgress.isInitialized) {
            volumeProgress.visibility = View.GONE
        }
    }

    private val volumeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            updateVolumeOverlay()
        }
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createOverlayView()

        val volumeUri = Settings.System.getUriFor(Settings.System.VOLUME_MUSIC)
        contentResolver.registerContentObserver(volumeUri, false, volumeObserver)

        updateVolumeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            contentResolver.unregisterContentObserver(volumeObserver)
        } catch (_: Exception) {
        }

        if (::volumeProgress.isInitialized) {
            try {
                windowManager.removeView(volumeProgress)
            } catch (_: Exception) {
            }
        }

        mainHandler.removeCallbacks(hideRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createOverlayView() {
        volumeProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            isIndeterminate = false
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9F1C"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#66000000"))
                cornerRadius = dpToPx(18).toFloat()
            }
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            layoutParams = ViewGroup.LayoutParams(dpToPx(220), dpToPx(18))
        }

        overlayParams = WindowManager.LayoutParams(
            dpToPx(220),
            dpToPx(22),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(100)
        }

        windowManager.addView(volumeProgress, overlayParams)
    }

    private fun updateVolumeOverlay() {
        if (!::volumeProgress.isInitialized) return

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        volumeProgress.max = maxVolume
        volumeProgress.progress = currentVolume
        volumeProgress.visibility = View.VISIBLE

        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, 2000)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
