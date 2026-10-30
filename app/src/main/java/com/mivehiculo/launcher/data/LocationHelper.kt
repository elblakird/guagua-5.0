package com.mivehiculo.launcher.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * Usa el LocationManager del propio Android. Se eligió en vez de FusedLocationProviderClient porque
 * muchos radios chinos NO tienen Google Play Services, y así no se añade ninguna librería.
 */
object LocationHelper {

    /** La ubicación guardada más reciente (red, GPS o pasiva). null si no hay ninguna. */
    fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val loc = try {
                lm.getLastKnownLocation(p)
            } catch (e: Exception) {
                null
            }
            if (loc != null && (best == null || loc.time > best.time)) best = loc
        }
        return best
    }

    /** Antigüedad de una ubicación, en milisegundos. */
    fun ageMs(loc: Location): Long = abs(System.currentTimeMillis() - loc.time)

    /** Pide UNA sola ubicación. Si no llega en [timeoutMs], devuelve null (nunca se queda esperando). */
    fun requestOnce(context: Context, timeoutMs: Long = 20_000L, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull {
            try {
                lm?.isProviderEnabled(it) == true
            } catch (e: Exception) {
                false
            }
        }
        if (lm == null || provider == null) {
            onResult(null)
            return
        }
        OneShot(lm, onResult).start(provider, timeoutMs)
    }

    private class OneShot(
        private val lm: LocationManager,
        private val onResult: (Location?) -> Unit,
    ) : LocationListener {
        private val handler = Handler(Looper.getMainLooper())
        private var done = false

        fun start(provider: String, timeoutMs: Long) {
            try {
                lm.requestSingleUpdate(provider, this, Looper.getMainLooper())
                handler.postDelayed({ finish(null) }, timeoutMs)
            } catch (e: Exception) {
                finish(null)
            }
        }

        override fun onLocationChanged(location: Location) = finish(location)

        private fun finish(loc: Location?) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            try {
                lm.removeUpdates(this)
            } catch (e: Exception) {
                // nada que hacer
            }
            onResult(loc)
        }
    }
}
