package com.mivehiculo.launcher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val packageName: String, val icon: ImageBitmap)

object Apps {

    /** Todas las apps instaladas que se pueden abrir, ordenadas por nombre. */
    fun installed(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Lanza un Intent SOLO si existe una app que lo pueda atender, y atrapa cualquier error.
     * Devuelve true si se abrió algo; false si no (nunca lanza excepciones).
     */
    fun start(context: Context, intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Prueba los Intents en orden y se detiene en el primero que funcione. */
    fun tryOpen(context: Context, vararg intents: Intent): Boolean =
        intents.any { start(context, it) }

    /** Abre una app por su nombre de paquete (false si no está instalada o no se pudo abrir). */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return start(context, intent)
    }

    /** Paquete que el usuario eligió antes en la lista (radio, cámara...). null si nunca eligió. */
    fun savedPackage(context: Context, key: String): String? = try {
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString(key, null)
    } catch (e: Exception) {
        null
    }

    /** Prueba la lista de paquetes en orden y abre la primera app que funcione. */
    fun launchAny(context: Context, candidates: List<String>): Boolean =
        candidates.any { launch(context, it) }
}
