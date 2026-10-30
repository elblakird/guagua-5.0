package com.mivehiculo.launcher.data

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatea la hora y la fecha reales del sistema. */
object ClockFormat {
    private val es = Locale.forLanguageTag("es")

    /** Respeta el formato 12/24 h del radio. Locale.US asegura dígitos normales (0-9). */
    fun time(context: Context, now: Date): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.US).format(now)
    }

    /** Ej.: "Miércoles, 28 de octubre". */
    fun date(now: Date): String =
        SimpleDateFormat("EEEE, d 'de' MMMM", es).format(now).replaceFirstChar { it.uppercase() }
}
