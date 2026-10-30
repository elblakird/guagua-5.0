package com.mivehiculo.launcher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Clima ya listo para mostrar. */
data class WeatherData(val temp: Int, val feels: Int, val condition: String) {
    /** Ej.: "22°C · Nublado" */
    val headline: String get() = "$temp°C · $condition"

    /** Ej.: "Sensación 20°C" */
    val feelsLine: String get() = "Sensación $feels°C"
}

/**
 * Consulta OpenWeatherMap con HttpURLConnection + org.json (ya vienen en Android):
 * cero librerías extra, cero RAM adicional.
 */
object WeatherRepo {

    /** Devuelve el clima, o null si algo falla (sin internet, clave inválida...). Llamar fuera del hilo principal. */
    fun fetch(lat: Double, lon: Double, apiKey: String): WeatherData? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.openweathermap.org/data/2.5/weather" +
                    "?lat=$lat&lon=$lon&units=metric&lang=es&appid=$apiKey"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) return null
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(json: String): WeatherData {
        val root = JSONObject(json)
        val main = root.getJSONObject("main")
        val w = root.getJSONArray("weather").getJSONObject(0)
        return WeatherData(
            temp = main.getDouble("temp").roundToInt(),
            feels = main.getDouble("feels_like").roundToInt(),
            condition = condition(w.getInt("id"), w.optString("description")),
        )
    }

    private fun condition(id: Int, description: String): String = when (id) {
        in 200..232 -> "Tormenta"
        in 300..321 -> "Llovizna"
        in 500..531 -> "Lluvia"
        in 600..622 -> "Nieve"
        in 701..781 -> "Neblina"
        800 -> "Despejado"
        801, 802 -> "Poco nublado"
        803, 804 -> "Nublado"
        else -> description.replaceFirstChar { it.uppercase() }
    }
}
