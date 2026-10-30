package com.mivehiculo.launcher.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import java.util.Calendar

enum class ThemeMode { AUTO, DAY, NIGHT }

/**
 * Decide si se ve el Modo Día o el Modo Noche y lo aplica a la paleta.
 *
 *  - Manual (Día / Noche): manda lo que elijas.
 *  - Automático: es de noche si el sistema del radio lo indica (UiModeManager / configuración del sistema)
 *    O si la hora local está entre las 18:30 y las 06:30 (plan de respaldo).
 *
 * Es barato: leer la configuración y la hora cuesta microsegundos, así que se puede llamar cada minuto.
 */
object ThemeController {
    private const val NIGHT_START = 18 * 60 + 30 // 18:30
    private const val NIGHT_END = 6 * 60 + 30    // 06:30
    private const val PREFS = "launcher"
    private const val KEY = "theme_mode"

    /** Modo elegido (la pantalla de Mantenimiento lo lee para marcar el botón activo). */
    var mode by mutableStateOf(ThemeMode.AUTO)
        private set

    private var loaded = false
    private var windowDark: Boolean? = null

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
            mode = ThemeMode.values().getOrElse(saved) { ThemeMode.AUTO }
        } catch (e: Exception) {
            mode = ThemeMode.AUTO
        }
    }

    /** Control manual: guarda la elección y la aplica al instante. */
    fun setMode(activity: Activity, newMode: ThemeMode) {
        mode = newMode
        try {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, newMode.ordinal).apply()
        } catch (e: Exception) {
            // si no se pudo guardar, el cambio igual se aplica hasta que se cierre la app
        }
        sync(activity, force = true)
    }

    /**
     * Recalcula Día/Noche y actualiza la paleta. Los colores de la ventana (fondo, barras del sistema)
     * solo se tocan si cambió el tema, o si force = true (ventana nueva).
     */
    fun sync(activity: Activity, force: Boolean = false) {
        val dark = resolveDark(activity)
        Palette.setDark(dark)
        if (force || dark != windowDark) {
            windowDark = dark
            applyWindow(activity, dark)
        }
    }

    private fun resolveDark(context: Context): Boolean = when (mode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.AUTO -> systemSaysNight(context) || isNightHours()
    }

    /** Señal del sistema: configuración "noche" activa o UiModeManager en modo noche. */
    private fun systemSaysNight(context: Context): Boolean = try {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        night == Configuration.UI_MODE_NIGHT_YES || manager?.nightMode == UiModeManager.MODE_NIGHT_YES
    } catch (e: Exception) {
        false
    }

    /** Respaldo por hora local: de 18:30 a 06:30. */
    private fun isNightHours(): Boolean {
        val c = Calendar.getInstance()
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return minutes >= NIGHT_START || minutes < NIGHT_END
    }

    /** Fondo de la ventana y barras del sistema acordes al tema (evita destellos blancos en modo noche). */
    private fun applyWindow(activity: Activity, dark: Boolean) {
        try {
            val w = activity.window
            val color = Palette.BgTop.toArgb()
            w.setBackgroundDrawable(ColorDrawable(color))
            w.statusBarColor = color
            w.navigationBarColor = color
            WindowCompat.getInsetsController(w, w.decorView)?.isAppearanceLightStatusBars = !dark
        } catch (e: Exception) {
            // cosmético: nunca debe cerrar la app
        }
    }
}
