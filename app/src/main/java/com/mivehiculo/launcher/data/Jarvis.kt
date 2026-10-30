package com.mivehiculo.launcher.data

import com.mivehiculo.launcher.BuildConfig
import com.mivehiculo.launcher.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AiReply(val text: String, val ok: Boolean)

/**
 * Envía el texto a una API compatible con OpenAI (Groq por defecto) y devuelve la respuesta.
 * Se usa HttpURLConnection (ya viene en Android) en vez de OkHttp: cero librerías nuevas, menos RAM.
 * Nunca lanza excepciones: cualquier fallo vuelve como un mensaje corto. Llamar fuera del hilo principal.
 */
object Jarvis {

    fun ask(userText: String): AiReply {
        var conn: HttpURLConnection? = null
        return try {
            val body = JSONObject()
                .put("model", Config.API_MODEL)
                .put("max_tokens", 100)
                .put("temperature", 0.5)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", Config.SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", userText)),
                )
                .toString()

            conn = (URL(Config.API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            when (val code = conn.responseCode) {
                200 -> {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val content = JSONObject(json)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    val clean = content.replace(Regex("[*#_`]"), "").trim()
                    if (clean.isEmpty()) AiReply("No tengo una respuesta para eso.", false) else AiReply(clean, true)
                }
                401, 403 -> AiReply("La clave de la API no es válida.", false)
                429 -> AiReply("Estoy saturado, intenta de nuevo en un momento.", false)
                else -> AiReply("El servicio de IA devolvió un error $code.", false)
            }
        } catch (e: IOException) {
            AiReply("No tengo conexión a internet.", false)
        } catch (e: Exception) {
            AiReply("No pude procesar la respuesta.", false)
        } finally {
            conn?.disconnect()
        }
    }
}
