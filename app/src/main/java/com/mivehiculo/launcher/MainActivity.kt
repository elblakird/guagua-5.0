package com.mivehiculo.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationListener
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mivehiculo.launcher.data.AppEntry
import com.mivehiculo.launcher.data.Apps
import com.mivehiculo.launcher.data.ClockFormat
import com.mivehiculo.launcher.data.LocationHelper
import com.mivehiculo.launcher.data.MediaState
import com.mivehiculo.launcher.data.MediaWatcher
import com.mivehiculo.launcher.data.WeatherData
import com.mivehiculo.launcher.data.WeatherManager
import com.mivehiculo.launcher.ui.AppDrawer
import com.mivehiculo.launcher.ui.HomeActions
import com.mivehiculo.launcher.ui.HomeScreen
import com.mivehiculo.launcher.ui.ThemeController
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var drawerApps by mutableStateOf<List<AppEntry>?>(null)
    private var loadingApps = false
    private var pickTarget by mutableStateOf<String?>(null)

    private var time by mutableStateOf("")
    private var date by mutableStateOf("")
    private var weather by mutableStateOf<WeatherData?>(null)
    private var odometerMeters by mutableStateOf(0f)
    private var odometerListener: LocationListener? = null

    private var media by mutableStateOf(MediaState())
    private val mediaWatcher by lazy { MediaWatcher(this) { media = it } }
    private val weatherManager by lazy { WeatherManager(this) { weather = it } }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            if (grantResults[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grantResults[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                startOdometerTracking()
            }
            weatherManager.refresh()
        }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshClock()
            weatherManager.refreshIfDue()
            ThemeController.sync(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(this)
        ThemeController.sync(this, force = true)
        refreshClock()
        weather = weatherManager.cached()
        odometerMeters = LocationHelper.readOdometerMeters(this)

        if (!hasLocationPermission()) {
            try {
                locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } catch (_: Exception) {
                // Some firmwares do not show the runtime permission dialog; odometer keeps the stored value.
            }
        } else {
            startOdometerTracking()
        }

        if (weatherManager.shouldAskPermission()) {
            try {
                locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } catch (_: Exception) {
                // Some firmwares do not show the runtime permission dialog; weather remains optional.
            }
        }

        val actions = HomeActions(
            openRadio = { openRadioApp() },
            pickRadio = { pickRadioApp() },
            openMusic = {
                if (!Apps.start(this, Intent(this, MusicPlayerActivity::class.java))) toast("No se pudo abrir el reproductor")
            },
            openPhone = {
                open("Teléfono", Apps.tryOpen(this, Intent(Intent.ACTION_DIAL)) || Apps.launchAny(this, Config.TELEFONO))
            },
            openApps = { showApps() },
            openAndroidAuto = { open("Android Auto", Apps.launchAny(this, Config.ANDROID_AUTO)) },
            openNavigation = {
                open(
                    "Navegación",
                    Apps.launchAny(this, Config.NAVEGACION) ||
                        Apps.tryOpen(this, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)),
                )
            },
            openSettings = {
                open("Ajustes", Apps.tryOpen(this, Intent(Settings.ACTION_SETTINGS)) || Apps.launchAny(this, Config.AJUSTES))
            },
            mediaPrev = { mediaWatcher.previous() },
            mediaPlayPause = { mediaWatcher.playPause() },
            mediaNext = { mediaWatcher.next() },
            openAssistant = {
                if (!Apps.start(this, Intent(this, AssistantActivity::class.java))) toast("No se pudo abrir Jarvis")
            },
            openRearCamera = { openRearCamera() },
            pickRearCamera = { pickRearCamera() },
            openMaintenance = {
                if (!Apps.start(this, Intent(this, MaintenanceActivity::class.java))) toast("No se pudo abrir Mantenimiento")
            },
            openNotificationAccess = {
                if (!Apps.tryOpen(this, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                    Apps.tryOpen(this, Intent(Settings.ACTION_SETTINGS))
                    toast("Busca: Apps > Acceso especial > Acceso a notificaciones > Mi Launcher")
                }
            },
        )

        setContent {
            val apps = drawerApps
            if (apps != null) {
                BackHandler { closeDrawer() }
                AppDrawer(
                    apps = apps,
                    onLaunch = {
                        pickTarget?.let { key -> savePickedApp(key, it.packageName) }
                        if (!Apps.launch(this@MainActivity, it.packageName)) toast("No se pudo abrir ${it.label}")
                        closeDrawer()
                    },
                    onClose = { closeDrawer() },
                    title = when (pickTarget) {
                        "camera_pkg" -> "Elige la app de la cámara trasera"
                        "radio_pkg" -> "Elige la app de la Radio FM"
                        else -> "Aplicaciones"
                    },
                )
            } else {
                BackHandler { }
                val w = weather
                HomeScreen(
                    time,
                    date,
                    w?.headline ?: "--°C",
                    w?.feelsLine ?: "",
                    media,
                    formatOdometerKm(odometerMeters),
                    actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ThemeController.sync(this, force = true)
        refreshClock()
        if (hasLocationPermission()) startOdometerTracking()
        registerReceiver(
            timeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
        )
        weatherManager.refreshIfDue()
        mediaWatcher.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeController.sync(this)
    }

    override fun onStop() {
        mediaWatcher.stop()
        stopOdometerTracking()
        unregisterReceiver(timeReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeDrawer()
    }

    private fun refreshClock() {
        val now = Date()
        time = ClockFormat.time(this, now)
        date = ClockFormat.date(now)
    }

    private fun closeDrawer() {
        drawerApps = null
        pickTarget = null
    }

    private fun startOdometerTracking() {
        stopOdometerTracking()
        odometerListener = LocationHelper.startOdometerTracking(this) { totalMeters ->
            odometerMeters = totalMeters
        }
    }

    private fun stopOdometerTracking() {
        LocationHelper.stopOdometerTracking(this, odometerListener)
        odometerListener = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun formatOdometerKm(meters: Float): String =
        String.format(Locale.US, "%,.0f", meters / 1000f)

    private fun openRearCamera() {
        val saved = getSharedPreferences("launcher", MODE_PRIVATE).getString("camera_pkg", null)
        if (!Apps.launchAny(this, Config.CAMARA + listOfNotNull(saved))) pickRearCamera()
    }

    private fun pickRearCamera() {
        pickTarget = "camera_pkg"
        toast("Toca la app de cámara trasera / AV-IN de tu radio")
        showApps()
    }

    private fun openRadioApp() {
        val saved = Apps.savedPackage(this, "radio_pkg")
        if (!Apps.launchAny(this, listOfNotNull(saved) + Config.RADIO)) pickRadioApp()
    }

    private fun pickRadioApp() {
        pickTarget = "radio_pkg"
        toast("Toca la app de Radio FM de tu radio")
        showApps()
    }

    private fun savePickedApp(key: String, pkg: String) {
        try {
            getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(key, pkg).apply()
        } catch (_: Exception) {
            // No-op: if it cannot be saved, the user will be prompted again later.
        }
    }

    private fun showApps() {
        if (loadingApps) return
        loadingApps = true
        Thread {
            val list = try {
                Apps.installed(this)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                drawerApps = list
                loadingApps = false
            }
        }.start()
    }

    private fun open(nombre: String, opened: Boolean) {
        if (!opened) toast("$nombre: app no encontrada (ver Config.kt)")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
