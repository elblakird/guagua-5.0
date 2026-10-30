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
    private const val PREFS = "launcher"
    private const val KEY_ODOMETER_METERS = "odometer_meters"

    // Filtros para descartar lecturas de GPS poco fiables
    private const val MAX_LOCATION_ACCURACY_METERS = 15f
    private const val MAX_JUMP_METERS = 50f
    private const val MIN_SPEED_KMH_FOR_MOVEMENT = 2f
    private const val MAX_REASONABLE_SPEED_KMH = 200f

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

    fun readOdometerMeters(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_ODOMETER_METERS, 0f)

    fun saveOdometerMeters(context: Context, meters: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_ODOMETER_METERS, meters)
            .apply()
    }

    fun startOdometerTracking(context: Context, onUpdate: (Float, Float) -> Unit): LocationListener? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return null

        var lastLocation: Location? = lastKnown(context)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // 1) Ignorar lecturas con precisión mala
                if (!location.hasAccuracy() || location.accuracy > MAX_LOCATION_ACCURACY_METERS) {
                    return
                }

                val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f

                // 2) Evitar lecturas de "flotación" del GPS cuando el vehículo está detenido
                if (location.hasSpeed() && speedKmh < MIN_SPEED_KMH_FOR_MOVEMENT) {
                    lastLocation = location
                    return
                }

                // 3) Filtrar velocidades imposibles o claramente erróneas
                if (location.hasSpeed() && speedKmh > MAX_REASONABLE_SPEED_KMH) {
                    lastLocation = location
                    return
                }

                val previous = lastLocation
                if (previous != null && location.time >= previous.time) {
                    // 4) Ignorar salto de señal / pérdida de GPS
                    val deltaMeters = previous.distanceTo(location)
                    if (deltaMeters > MAX_JUMP_METERS) {
                        lastLocation = location
                        return
                    }

                    // 5) Evitar sumar distancias nulas o absurdamente pequeñas cuando hay ruido del GPS
                    if (deltaMeters <= 0f) {
                        lastLocation = location
                        return
                    }

                    // 6) Sumar solo distancias razonables
                    if (deltaMeters < 10_000f) {
                        val totalMeters = readOdometerMeters(context) + deltaMeters
                        saveOdometerMeters(context, totalMeters)
                        onUpdate(totalMeters, speedKmh)
                    }
                }

                lastLocation = location
                if (previous == null) {
                    onUpdate(readOdometerMeters(context), speedKmh)
                }
            }
        }

        try {
            // 7) Actualización cada 1 segundo o cada 1 metro
            lm.requestLocationUpdates(provider, 1_000L, 1f, listener, Looper.getMainLooper())
            return listener
        } catch (e: Exception) {
            return null
        }
    }

    fun stopOdometerTracking(context: Context, listener: LocationListener?) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (listener != null) {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
                // no-op
            }
        }
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
