package com.mivehiculo.launcher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si el sistema pide permiso para overlay, lo pedimos.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
            return
        }

        startVolumeOverlay()
    }

    private fun startVolumeOverlay() {
        val intent = Intent(this, VolumeOverlayService::class.java)
        startService(intent)
    }
}
