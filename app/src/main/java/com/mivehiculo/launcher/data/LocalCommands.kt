package com.mivehiculo.launcher.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.mivehiculo.launcher.Config
import com.mivehiculo.launcher.MaintenanceActivity
import com.mivehiculo.launcher.MusicPlayerActivity
import java.text.Normalizer
import java.util.Locale

enum class Command(val confirmation: String) {
    MUSIC("Abriendo la música."),
    SPOTIFY("Abriendo Spotify."),
    MAPS("Abriendo Google Maps."),
    WAZE("Abriendo Waze."),
    NAV("Abriendo la navegación."),
    PHONE("Abriendo el teléfono."),
    RADIO("Abriendo la radio."),
    SETTINGS("Abriendo los ajustes."),
    ANDROID_AUTO("Abriendo Android Auto."),
    MAINTENANCE("Abriendo mantenimiento."),
}

/** Reconoce órdenes simples como "abre la música" o "abre Google Maps" con condicionales: 0 tokens de IA. */
object LocalCommands {

    private val verbs = setOf(
        "abre", "abrir", "abra", "abrime", "inicia", "iniciar", "ejecuta", "lanza",
        "pon", "pone", "ponme", "reproduce", "enciende",
    )

    /** Minúsculas, sin tildes ni signos: "¡Ábre la Música!" -> "abre la musica". */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun match(raw: String): Command? {
        val t = normalize(raw)
        val words = t.split(" ")
        // Solo frases cortas con un verbo de "abrir": así una pregunta larga va a la IA y no abre apps por error.
        if (words.size > 6 || words.none { it in verbs }) return null
        return when {
            "waze" in words -> Command.WAZE
            t.contains("google maps") || "maps" in words || "mapas" in words || "mapa" in words -> Command.MAPS
            t.contains("android auto") -> Command.ANDROID_AUTO
            "spotify" in words -> Command.SPOTIFY
            "musica" in words -> Command.MUSIC
            "radio" in words -> Command.RADIO
            "telefono" in words || "marcador" in words -> Command.PHONE
            "ajustes" in words || "configuracion" in words -> Command.SETTINGS
            "mantenimiento" in words -> Command.MAINTENANCE
            "navegacion" in words || "navegador" in words || "gps" in words -> Command.NAV
            else -> null
        }
    }
}

/** Lanza la app de cada comando con los mismos Intents seguros del Dock. Devuelve false si no se pudo. */
object Shortcuts {
    fun open(context: Context, cmd: Command): Boolean = when (cmd) {
        Command.MUSIC -> Apps.start(context, Intent(context, MusicPlayerActivity::class.java))
        Command.SPOTIFY -> Apps.launch(context, "com.spotify.music") || Apps.launchAny(context, Config.MUSICA)
        Command.MAPS -> Apps.launch(context, "com.google.android.apps.maps") || Apps.launchAny(context, Config.NAVEGACION)
        Command.WAZE -> Apps.launch(context, "com.waze") || Apps.launchAny(context, Config.NAVEGACION)
        Command.NAV ->
            Apps.launchAny(context, Config.NAVEGACION) ||
                Apps.tryOpen(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS))
        Command.PHONE -> Apps.tryOpen(context, Intent(Intent.ACTION_DIAL)) || Apps.launchAny(context, Config.TELEFONO)
        Command.RADIO -> Apps.launchAny(context, listOfNotNull(Apps.savedPackage(context, "radio_pkg")) + Config.RADIO)
        Command.SETTINGS -> Apps.tryOpen(context, Intent(Settings.ACTION_SETTINGS)) || Apps.launchAny(context, Config.AJUSTES)
        Command.ANDROID_AUTO -> Apps.launchAny(context, Config.ANDROID_AUTO)
        Command.MAINTENANCE -> Apps.start(context, Intent(context, MaintenanceActivity::class.java))
    }
}
