package com.mivehiculo.launcher

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mivehiculo.launcher.data.MaintenanceData
import com.mivehiculo.launcher.data.MaintenanceRepo
import com.mivehiculo.launcher.ui.MaintenanceActions
import com.mivehiculo.launcher.ui.MaintenanceScreen
import com.mivehiculo.launcher.ui.Overlay
import com.mivehiculo.launcher.ui.ThemeController
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Pantalla de mantenimiento. Todo el acceso a la base de datos va en un hilo aparte, en orden. */
class MaintenanceActivity : ComponentActivity() {

    private val repo by lazy { MaintenanceRepo.get(this) }
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var data by mutableStateOf(MaintenanceData())
    private var overlay by mutableStateOf(Overlay.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(this)
        ThemeController.sync(this, force = true)

        val actions = MaintenanceActions(
            back = { finish() },
            pickInterval = { km -> write { repo.setInterval(km) } },
            // Sin kilometraje actual no se puede registrar el cambio: primero se pide el kilometraje.
            requestChange = { overlay = if (data.currentKm > 0) Overlay.Confirm else Overlay.CurrentKm },
            openCurrentKm = { overlay = Overlay.CurrentKm },
            openLastKm = { overlay = Overlay.LastKm },
            dismiss = { overlay = Overlay.None },
            confirmChange = {
                overlay = Overlay.None
                val km = data.currentKm
                write { repo.registerChange(System.currentTimeMillis(), km) }
            },
            saveCurrentKm = { km ->
                overlay = Overlay.None
                write { repo.setCurrentKm(km) }
            },
            saveLastKm = { km ->
                overlay = Overlay.None
                write { repo.setLastChangeKm(km) }
            },
            setThemeMode = { ThemeController.setMode(this, it) },
        )

        setContent {
            // "Atrás" cierra primero la ventana flotante; si no hay, sale de la pantalla.
            BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
            MaintenanceScreen(data, overlay, ThemeController.mode, actions)
        }

        write { } // carga inicial
    }

    override fun onStart() {
        super.onStart()
        ThemeController.sync(this, force = true)
    }

    /** El sistema cambió de modo (p. ej. se encendieron las luces): se recalcula el tema. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeController.sync(this)
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    /** Ejecuta la escritura en el hilo de base de datos y luego refresca la pantalla con lo guardado. */
    private fun write(block: () -> Unit) {
        try {
            io.execute {
                try {
                    block()
                } catch (e: Exception) {
                    // un fallo de disco no debe cerrar la app: se vuelve a leer lo que haya guardado
                }
                val fresh = try {
                    repo.load()
                } catch (e: Exception) {
                    null
                }
                if (fresh != null) runOnUiThread { data = fresh }
            }
        } catch (e: RejectedExecutionException) {
            // la pantalla ya se cerró
        }
    }
}
