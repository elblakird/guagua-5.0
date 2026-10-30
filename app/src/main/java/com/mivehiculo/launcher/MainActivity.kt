package com.mivehiculo.launcher

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
           // startService(android.content.Intent(this, VolumeOverlayService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "¡La app funciona! Vamos por el diseño.", color = Color.Red)
            }
        }
    }

    companion object {
        private const val PREFS = "launcher"
        private const val KEY_ODOMETER_METERS = "odometer_meters"
        private const val MAX_LOCATION_ACCURACY_METERS = 15f
        private const val MAX_JUMP_METERS = 50f
    }

    private fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun ageMs(loc: Location): Long = abs(System.currentTimeMillis() - loc.time)

    fun requestOnce(context: Context, timeoutMs: Long = 20_000L, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onResult(location)
                lm.removeUpdates(this)
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
            Handler(Looper.getMainLooper()).postDelayed({ 
                lm.removeUpdates(listener)
                onResult(null) 
            }, timeoutMs)
        } catch (e: Exception) {
            onResult(null)
        }
    }
}

