package com.mivehiculo.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
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
import com.mivehiculo.launcher.data.AppEntry
import com.mivehiculo.launcher.data.Apps
import com.mivehiculo.launcher.data.ClockFormat
import com.mivehiculo.launcher.data.MediaState
import com.mivehiculo.launcher.data.MediaWatcher
import com.mivehiculo.launcher.data.WeatherData
import com.mivehiculo.launcher.data.WeatherManager
import com.mivehiculo.launcher.ui.AppDrawer
import com.mivehiculo.launcher.ui.HomeActions
import com.mivehiculo.launcher.ui.HomeScreen
import com.mivehiculo.launcher.ui.ThemeController
import java.util.Date

class MainActivity : ComponentActivity() {

    /** null = pantalla de inicio; con valor = lista de apps abierta. */
    private var drawerApps by mutableStateOf<List<AppEntry>?>(null)
    private var loadingApps = false

    /** Qué app se está ELIGIENDO en la lista: "camera_pkg", "radio_pkg" o null (lista normal). */
    private var pickTarget by mutableStateOf<String?>(null)

    private var time by mutableStateOf("")
    private var date by mutableStateOf("")
    private var weather by mutableStateOf<WeatherData?>(null)

    private var media by mutableStateOf(MediaState())
    // "by lazy": una Activity no puede usar su contexto antes de onCreate (si no, la app se cierra al abrir).
    private val mediaWatcher by lazy { MediaWatcher(this) { media = it } }

    private val weatherManager by lazy { WeatherManager(this) { weather = it } }

    /** Pide el permiso de ubicación con el diálogo estándar de Android; al responder, actualiza el clima. */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            weatherManager.refresh()
        }

    /** El sistema avisa solo cada minuto (TIME_TICK): no hay temporizadores ni bucles. */
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshClock()
            weatherManager.refreshIfDue()
            ThemeController.sync(this@MainActivity) // respaldo por hora: 18:30 - 06:30
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(this)
        ThemeController.sync(this, force = true)
        refreshClock()
        weather = weatherManager.cached()

        if (weatherManager.shouldAskPermission()) {
            try {
                locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } catch (e: Exception) {
                // Algunos firmwares no traen el diálogo de permisos: la app sigue sin clima.
            }
        }

        val actions = HomeActions(
            openRadio = { openRadioApp() },
            pickRadio = { pickRadioApp() },
            // El botón Música abre nuestro reproductor local (USB), no la app nativa.
            openMusic = {
                if (!Apps.start(this, Intent(this, MusicPlayerActivity::class.java))) toast("No se pudo abrir el reproductor")
            },
            openPhone = {
                // Marcador predeterminado; si no hay, la app de Bluetooth/teléfono del radio.
                open("Teléfono", Apps.tryOpen(this, Intent(Intent.ACTION_DIAL)) || Apps.launchAny(this, Config.TELEFONO))
            },
            openApps = { showApps() },
            openAndroidAuto = { open("Android Auto", Apps.launchAny(this, Config.ANDROID_AUTO)) },
            openNavigation = {
                // Google Maps o Waze (lo que esté instalado); si no, cualquier app de mapas del sistema.
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
                // Pantalla "Acceso a notificaciones"; si el radio no la trae, abre Ajustes y avisa dónde buscar.
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
                BackHandler { /* en la pantalla de inicio, "atrás" no hace nada */ }
                val w = weather
                HomeScreen(time, date, w?.headline ?: "--°C", w?.feelsLine ?: "", media, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ThemeController.sync(this, force = true)
        refreshClock()
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

    /** El sistema cambió de modo (p. ej. se encendieron las luces): se recalcula el tema al instante. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeController.sync(this)
    }

    /** Cuando el launcher no está a la vista, se deja de escuchar: consumo cero. */
    override fun onStop() {
        mediaWatcher.stop()
        unregisterReceiver(timeReceiver)
        super.onStop()
    }

    /** Al pulsar el botón Home estando ya en inicio, se cierra la lista de apps. */
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

    /**
     * Botón "Cámara trasera" (respaldo manual). NO toca el video ni la MCU: solo abre la app de cámara
     * / AV-IN que instaló el fabricante, con su Intent de lanzador normal.
     * Orden: Config.CAMARA -> app elegida antes por el usuario -> si no hay ninguna, deja elegirla.
     */
    private fun openRearCamera() {
        val saved = getSharedPreferences("launcher", MODE_PRIVATE).getString("camera_pkg", null)
        if (!Apps.launchAny(this, Config.CAMARA + listOfNotNull(saved))) pickRearCamera()
    }

    /** Abre la lista de apps para elegir cuál es la cámara trasera (también con pulsación larga en el Dock). */
    private fun pickRearCamera() {
        pickTarget = "camera_pkg"
        toast("Toca la app de cámara trasera / AV-IN de tu radio")
        showApps()
    }

    /**
     * Botón "Radio": abre la app de Radio FM NATIVA del sistema (no se rediseña: la controla el chip del radio).
     * Android no tiene una categoría estándar para "radio" (como sí la hay para música o mapas), así que se usa
     * el Intent de lanzador normal de esa app: primero la que elegiste, luego la lista de Config.RADIO y, si no
     * hay ninguna, te deja elegirla una sola vez. Mantén pulsado el botón para cambiarla.
     * La app se abre en su propia tarea: con "Atrás" o el botón Home vuelves al launcher.
     */
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
        } catch (e: Exception) {
            // si no se guarda, simplemente volverá a preguntar
        }
    }

    /** La lista de apps se arma en un hilo aparte para no congelar la pantalla. */
    private fun showApps() {
        if (loadingApps) return
        loadingApps = true
        Thread {
            val list = try {
                Apps.installed(this)
            } catch (e: Exception) {
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
