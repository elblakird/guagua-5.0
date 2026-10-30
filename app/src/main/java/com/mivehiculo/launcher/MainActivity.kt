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
import androidx.compose.runtime.*
import com.mivehiculo.launcher.data.MediaState
import com.mivehiculo.launcher.ui.HomeActions
import com.mivehiculo.launcher.ui.HomeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Iniciar la barra flotante de volumen
        startService(android.content.Intent(this, VolumeOverlayService::class.java))

        // 2. Dibujar la pantalla principal
        <service android:name=".VolumeOverlayService" android:enabled="true" android:exported="false" />

            // Esto actualiza el reloj cada minuto
            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = timeFormat.format(Date())
                    currentDate = dateFormat.format(Date())
                    kotlinx.coroutines.delay(60000L)
                }
            }

            // Creamos las acciones (los botones). Por ahora muestran un Toast para probar.
            val actions = HomeActions(
                openRadio = { toast("Abriendo Radio") },
                openMusic = { toast("Abriendo Música") },
                openPhone = { toast("Abriendo Teléfono") },
                openApps = { toast("Abriendo Apps") },
                openAndroidAuto = { toast("Abriendo Android Auto") },
                openNavigation = { toast("Abriendo Navegación") },
                openSettings = { toast("Abriendo Ajustes") },
                mediaPrev = { toast("Anterior") },
                mediaPlayPause = { toast("Play/Pausa") },
                mediaNext = { toast("Siguiente") },
                openNotificationAccess = { toast("Permiso de Notificaciones") },
                openMaintenance = { toast("Mantenimiento") },
                openRearCamera = { toast("Cámara Trasera") },
                pickRearCamera = { toast("Seleccionar Cámara") },
                pickRadio = { toast("Seleccionar Radio") },
                openAssistant = { toast("Abriendo Jarvis") }
            )

            // Llamamos a tu pantalla con datos de prueba
            HomeScreen(
                time = currentTime,
                date = currentDate,
                weather = "24°C",
                feels = "Despejado",
                media = MediaState(), // Estado vacío de música para que no falle
                odometerKm = "0", 
                actions = actions
            )
        }
    }

    // Función auxiliar para los Toasts
    fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    // --- AQUI EMPIEZA TU LÓGICA DE GPS ORIGINAL ---
    companion object {
        private const val PREFS = "launcher"
        private const val KEY_ODOMETER_METERS = "odometer_meters"
        private const val MAX_LOCATION_ACCURACY_METERS = 15f
        private const val MAX_JUMP_METERS = 50f
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
