package com.mivehiculo.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import com.mivehiculo.launcher.data.MediaState
import com.mivehiculo.launcher.ui.HomeActions
import com.mivehiculo.launcher.ui.HomeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // Variables para el GPS
    private var currentSpeedKmh by mutableStateOf("0")
    private var totalOdometer by mutableStateOf("0.0")
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var totalMeters: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos de ubicación
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }

        // Iniciar el GPS
        setupGPS()

        setContent {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            
            var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }
            var currentDate by remember { mutableStateOf(dateFormat.format(Date())) }

            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = timeFormat.format(Date())
                    currentDate = dateFormat.format(Date())
                    kotlinx.coroutines.delay(60000L)
                }
            }

            val actions = HomeActions(
                openRadio = { openAppByPackage("com.jancar.radio") },
                openMusic = { openAppByPackage("com.jancar.music") },
                openPhone = { try { startActivity(Intent(Intent.ACTION_DIAL)) } catch(e: Exception) {} },
                openApps = { toast("Apps") },
                openAndroidAuto = { openAppByPackage("com.google.android.projection.sink") },
                openNavigation = { openAppByPackage("com.google.android.apps.maps") },
                openSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                mediaPrev = { toast("Anterior") },
                mediaPlayPause = { toast("Play/Pausa") },
                mediaNext = { toast("Siguiente") },
                openNotificationAccess = { toast("Permiso Notificaciones") },
                openMaintenance = { startActivity(Intent(this@MainActivity, MaintenanceActivity::class.java)) },
                openRearCamera = { toast("Cámara") },
                pickRearCamera = { toast("Elegir Cámara") },
                pickRadio = { toast("Elegir Radio") },
                openAssistant = { startActivity(Intent(this@MainActivity, AssistantActivity::class.java)) }
            )

            HomeScreen(
                time = currentTime,
                date = currentDate,
                weather = "24°C", // Luego lo hacemos real
                feels = "Despejado",
                media = MediaState(), 
                odometerKm = totalOdometer, // Pasamos el odómetro real
                speedKmh = currentSpeedKmh, // ¡Ojo! Necesitamos asegurarnos de que HomeScreen reciba esto
                actions = actions
            )
        }
    }

    // Lógica del GPS
    private fun setupGPS() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Velocidad en km/h
                    val speed = location.speed * 3.6f
                    currentSpeedKmh = if (speed > 0) String.format("%.0f", speed) else "0"

                    // Distancia acumulada (Odómetro)
                    if (lastLocation != null) {
                        val distance = lastLocation!!.distanceTo(location)
                        if (distance > 1.0) { // Ignorar si se movió menos de 1 metro
                            totalMeters += distance
                            totalOdometer = String.format("%.1f", totalMeters / 1000.0)
                        }
                    }
                    lastLocation = location
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
        }
    }

    private fun openAppByPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            toast("App no encontrada en este dispositivo")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
