package com.mivehiculo.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Service
import android.content.ContentObserver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.max

class VolumeOverlayService : Service() {

    companion object {
        private const val AUTO_HIDE_DELAY_MS = 2_000L
        private const val OVERLAY_WIDTH_DP = 260f
        private const val OVERLAY_HEIGHT_DP = 46f
        private const val OVERLAY_TOP_MARGIN_DP = 110f
        private const val OVERLAY_BG_COLOR = 0xB3191919.toInt()
        private const val ORANGE_ACCENT = "#FF8A1E"
    }

    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager
    private lateinit var overlayContainer: FrameLayout
    private lateinit var volumeBarView: VolumeBarView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    private val volumeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            updateVolume()
            showOverlay()
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createOverlay()
        registerVolumeObserver()
        updateVolume()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterVolumeObserver()
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlayIfAttached()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        updateVolume()
        showOverlay()
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        volumeBarView = VolumeBarView(this)

        val params = WindowManager.LayoutParams(
            dpToPx(OVERLAY_WIDTH_DP).toInt(),
            dpToPx(OVERLAY_HEIGHT_DP).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dpToPx(OVERLAY_TOP_MARGIN_DP).toInt()
        }

        overlayContainer = FrameLayout(this).apply {
            alpha = 0f
            isClickable = false
            isFocusable = false
            background = createOverlayBackground()
            addView(
                volumeBarView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(
                        dpToPx(10f).toInt(),
                        dpToPx(10f).toInt(),
                        dpToPx(10f).toInt(),
                        dpToPx(10f).toInt()
                    )
                }
            )
        }

        try {
            windowManager.addView(overlayContainer, params)
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun createOverlayBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(18f)
        setColor(OVERLAY_BG_COLOR)
        setStroke(dpToPx(1f).toInt(), Color.argb(90, 255, 255, 255))
    }

    private fun updateVolume() {
        if (!this::overlayContainer.isInitialized) return

        val maxVolume = max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val progress = currentVolume.toFloat() / maxVolume.toFloat()
        volumeBarView.setProgress(progress)
    }

    private fun showOverlay() {
        if (!this::overlayContainer.isInitialized) return

        overlayContainer.visibility = View.VISIBLE
        overlayContainer.animate().cancel()
        overlayContainer.alpha = 1f

        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun hideOverlay() {
        if (!this::overlayContainer.isInitialized) return

        overlayContainer.animate().cancel()
        overlayContainer.animate()
            .alpha(0f)
            .setDuration(180L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlayContainer.visibility = View.GONE
                    overlayContainer.alpha = 0f
                }
            })
            .start()
    }

    private fun registerVolumeObserver() {
        try {
            val uri = Settings.System.getUriFor("volume_music")
            contentResolver.registerContentObserver(uri, false, volumeObserver)
        } catch (_: Exception) {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        }
    }

    private fun unregisterVolumeObserver() {
        try {
            contentResolver.unregisterContentObserver(volumeObserver)
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun removeOverlayIfAttached() {
        if (!this::overlayContainer.isInitialized) return

        try {
            if (overlayContainer.isAttachedToWindow) {
                windowManager.removeViewImmediate(overlayContainer)
            }
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private class VolumeBarView(context: Context) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(ORANGE_ACCENT)
        }

        private var progress = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val width = width.toFloat()
            val height = height.toFloat()
            val radius = height * 0.5f

            canvas.drawRoundRect(RectF(0f, 0f, width, height), radius, radius, trackPaint)

            val fillWidth = width * progress.coerceIn(0f, 1f)
            if (fillWidth > 0f) {
                canvas.drawRoundRect(RectF(0f, 0f, fillWidth, height), radius, radius, fillPaint)
            }
        }

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }
    }
}
