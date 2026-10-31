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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var currentSpeedKmh by mutableStateOf("0")
    private var totalOdometer by mutableStateOf("0.0")
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var totalMeters: Double = 0.0

    private var weatherTemp by mutableStateOf("--°C")
    private var weatherDesc by mutableStateOf("Conectando...")
    private var hasFetchedWeather = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }

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
                openPhone = { 
                    try {
                        // 1. Intentar abrir el marcador estándar de Android
                        startActivity(Intent(Intent.ACTION_DIAL))
                    } catch (e: Exception) {
                        try {
                            // 2. Si falla, intentar con el paquete de marcador común
                            val intent = packageManager.getLaunchIntentForPackage("com.android.dialer")
                            if (intent != null) startActivity(intent) else toast("No se encontró app de teléfono")
                        } catch (e2: Exception) {
                            toast("No se pudo abrir la app de teléfono")
                        }
                    }
                },
                openApps = { 
                    try { startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS)) } catch(e: Exception) {}
                },
                openAndroidAuto = { openAppByPackage("com.google.android.projection.sink") },
                openNavigation = { openAppByPackage("com.google.android.apps.maps") },
                openSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                mediaPrev = { toast("Anterior") },
                mediaPlayPause = { toast("Play/Pausa") },
                mediaNext = { toast("Siguiente") },
                openNotificationAccess = { toast("Permiso Notificaciones") },
                openMaintenance = { toast("Mantenimiento en construcción") }, 
                openRearCamera = { toast("Cámara") },
                pickRearCamera = { toast("Elegir Cámara") },
                pickRadio = { toast("Elegir Radio") },
                openAssistant = { 
                    try { startActivity(Intent(this@MainActivity, AssistantActivity::class.java)) } 
                    catch(e: Exception) { toast("Error al abrir Jarvis") }
                }
            )

            HomeScreen(
                time = currentTime,
                date = currentDate,
                weather = weatherTemp,
                feels = weatherDesc,
                media = MediaState(), 
                odometerKm = totalOdometer,
                speedKmh = currentSpeedKmh, 
                actions = actions
            )
        }
    }

    private fun setupGPS() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val speed = location.speed * 3.6f
                    currentSpeedKmh = if (speed > 0) String.format("%.0f", speed) else "0"
                    if (lastLocation != null) {
                        val distance = lastLocation!!.distanceTo(location)
                        if (distance > 1.0) {
                            totalMeters += distance
                            totalOdometer = String.format("%.1f", totalMeters / 1000.0)
                        }
                    }
                    lastLocation = location
                    if (!hasFetchedWeather) {
                        hasFetchedWeather = true
                        fetchWeather(location.latitude, location.longitude)
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, listener)
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val currentWeather = json.getJSONObject("current_weather")
                    val temp = currentWeather.getDouble("temperature")
                    val code = currentWeather.getInt("weathercode")
                    val descripcion = when (code) {
                        0 -> "Despejado"
                        1, 2, 3 -> "Nublado"
                        45, 48 -> "Niebla"
                        51, 53, 55, 56, 57 -> "Llovizna"
                        61, 63, 65, 66, 67 -> "Lluvia"
                        71, 73, 75, 77 -> "Nieve"
                        80, 81, 82 -> "Chubascos"
                        95, 96, 99 -> "Tormenta"
                        else -> "Desconocido"
                    }
                    runOnUiThread { weatherTemp = "${String.format("%.0f", temp)}°C"; weatherDesc = descripcion }
                }
            } catch (e: Exception) {
                runOnUiThread { weatherTemp = "--°C"; weatherDesc = "Sin internet" }
            }
        }
    }

    private fun openAppByPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            toast("App no encontrada")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
