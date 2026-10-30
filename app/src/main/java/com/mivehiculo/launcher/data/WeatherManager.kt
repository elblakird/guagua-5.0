package com.mivehiculo.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.mivehiculo.launcher.Config
import kotlin.math.abs

/**
 * Decide cuándo y cómo actualizar el clima. Sin temporizadores propios: se le llama desde
 * el aviso de "cada minuto" del sistema y solo actúa si ya toca (cada 20 min; 5 min tras un fallo).
 * Siempre se usa desde el hilo principal; la única tarea en segundo plano es la llamada HTTP.
 */
class WeatherManager(context: Context, private val onChange: (WeatherData?) -> Unit) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private var loading = false
    private var showing = false
    private var nextAt = 0L // SystemClock.elapsedRealtime()

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** true solo la PRIMERA vez que se abre la app sin permiso (no se insiste en cada arranque). */
    fun shouldAskPermission(): Boolean {
        if (hasLocationPermission() || prefs.getBoolean("asked", false)) return false
        prefs.edit().putBoolean("asked", true).apply()
        return true
    }

    /** Último clima guardado, si tiene menos de 3 horas. */
    fun cached(): WeatherData? {
        val ts = prefs.getLong("ts", 0L)
        if (ts == 0L || abs(System.currentTimeMillis() - ts) > MAX_AGE_MS) return null
        val cond = prefs.getString("cond", null) ?: return null
        return WeatherData(prefs.getInt("temp", 0), prefs.getInt("feels", 0), cond).also { showing = true }
    }

    fun refreshIfDue() {
        // Si el dato mostrado ya es muy viejo, se vuelve a "--°C" en lugar de mentir.
        if (showing && abs(System.currentTimeMillis() - prefs.getLong("ts", 0L)) > MAX_AGE_MS) {
            showing = false
            onChange(null)
        }
        if (SystemClock.elapsedRealtime() >= nextAt) refresh()
    }

    fun refresh() {
        if (loading) return
        val key = Config.OPENWEATHER_API_KEY.trim()
        if (key.isEmpty()) return // sin clave: no se hace ninguna consulta

        loading = true
        nextAt = SystemClock.elapsedRealtime() + RETRY_MS
        try {
            if (!hasLocationPermission()) {
                start(null, key) // sin permiso: solo se puede usar la última ubicación guardada
                return
            }
            val last = LocationHelper.lastKnown(app)
            if (last != null && LocationHelper.ageMs(last) < FRESH_LOCATION_MS) {
                start(last, key)
            } else {
                LocationHelper.requestOnce(app) { loc -> start(loc ?: last, key) }
            }
        } catch (e: Exception) {
            loading = false
        }
    }

    /** Con una ubicación (o la última guardada) lanza la consulta en un hilo aparte. */
    private fun start(loc: Location?, key: String) {
        val lat: Double
        val lon: Double
        if (loc != null) {
            lat = loc.latitude
            lon = loc.longitude
            prefs.edit().putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
        } else {
            val sLat = prefs.getString("lat", null)?.toDoubleOrNull()
            val sLon = prefs.getString("lon", null)?.toDoubleOrNull()
            if (sLat == null || sLon == null) {
                loading = false // sin GPS ni ubicación guardada: se deja lo que haya en pantalla
                return
            }
            lat = sLat
            lon = sLon
        }
        Thread {
            val w = WeatherRepo.fetch(lat, lon, key)
            main.post { finish(w) }
        }.start()
    }

    private fun finish(w: WeatherData?) {
        loading = false
        if (w == null) return // sin internet o error de la API: se conserva lo mostrado
        nextAt = SystemClock.elapsedRealtime() + OK_MS
        showing = true
        prefs.edit()
            .putInt("temp", w.temp).putInt("feels", w.feels)
            .putString("cond", w.condition).putLong("ts", System.currentTimeMillis())
            .apply()
        onChange(w)
    }

    private companion object {
        const val OK_MS = 20 * 60_000L
        const val RETRY_MS = 5 * 60_000L
        const val MAX_AGE_MS = 3 * 60 * 60_000L
        const val FRESH_LOCATION_MS = 30 * 60_000L
    }
}
