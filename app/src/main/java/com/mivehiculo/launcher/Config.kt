package com.mivehiculo.launcher

/**
 * AQUÍ se personaliza lo propio de tu radio.
 *
 * Cada botón abre la PRIMERA app instalada de su lista. Si tu radio usa otra
 * app, añade su "nombre de paquete" al principio de la lista.
 * (Cómo averiguarlo: ver README.md, sección "Nombres de paquete".)
 */
object Config {
    /** Nombre que aparece arriba a la izquierda. */
    const val VEHICULO = "Hyundai Santa Fe"

    /** Mensaje de bienvenida (arriba a la izquierda, sobre la fecha). Cámbialo a tu gusto. */
    const val BIENVENIDA = "Bienvenido Eric / Wanda"

    /**
     * Clave de OpenWeatherMap. NO se escribe aquí: se inyecta al compilar (secreto OPENWEATHER_API_KEY, ver README).
     * Si no hay clave, el clima muestra "--°C".
     */
    val OPENWEATHER_API_KEY: String = BuildConfig.OPENWEATHER_API_KEY

    // Suposiciones habituales: ajústalas a tu radio.
    val RADIO = listOf("com.jancar.radio", "com.syu.radio", "com.mediatek.fmradio", "com.android.fmradio")
    val MUSICA = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.android.music")
    val TELEFONO = listOf("com.jancar.bluetooth", "com.syu.bt", "com.google.android.dialer", "com.android.dialer")
    val NAVEGACION = listOf("com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus", "app.organicmaps")
    val ANDROID_AUTO = listOf("com.google.android.projection.gearhead")
    val AJUSTES = listOf("com.android.settings")

    /**
     * App de cámara trasera / AV-IN que instaló el fabricante de TU radio. OPCIONAL.
     * Se deja vacía a propósito: el nombre cambia según el firmware y no se debe adivinar.
     * Si la dejas vacía, la primera vez que toques "Cámara trasera" la app te deja ELEGIRLA de la lista
     * de apps instaladas y se acuerda de tu elección. Si ya conoces el paquete, ponlo aquí
     * (ver README: "Cámara trasera").
     */
    val CAMARA: List<String> = emptyList()

    /**
     * Carpetas extra donde el reproductor busca música (además de detectar solo cada USB en /storage).
     * Si no existen, simplemente se ignoran.
     */
    val RUTAS_MUSICA = listOf("/storage/USB2/musica usb")

    // ---------------- Jarvis (asistente de voz con IA) ----------------

    /** Dirección y modelo de la IA (Groq, compatible con OpenAI). Si el modelo se retira, cámbialo aquí. */
    const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    const val API_MODEL = "llama-3.1-8b-instant"

    const val SYSTEM_PROMPT =
        "Eres Jarvis, el asistente del coche. Responde de forma muy breve y directa (máximo 20 palabras) para no distraer al conductor"

    /** Idioma de la voz: "es-ES", "es-MX", "es-US", "es-DO"... según lo que tenga tu radio. */
    const val IDIOMA_VOZ = "es-ES"
}
