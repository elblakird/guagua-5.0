package com.mivehiculo.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mivehiculo.launcher.data.Command
import com.mivehiculo.launcher.data.Jarvis
import com.mivehiculo.launcher.data.LocalCommands
import com.mivehiculo.launcher.data.Shortcuts
import com.mivehiculo.launcher.ui.AssistantScreen
import com.mivehiculo.launcher.ui.ThemeController
import com.mivehiculo.launcher.ui.VoiceState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Jarvis: presionar para hablar (sin escucha continua).
 * Voz -> audio (MediaRecorder) -> Whisper (Groq) -> texto -> comando local o IA -> texto -> voz (TextToSpeech).
 * Al salir de la pantalla se libera todo: micrófono, voz sintética y red.
 */
class AssistantActivity : ComponentActivity() {

    private var state by mutableStateOf(VoiceState.Idle)
    private var heard by mutableStateOf("")
    private var reply by mutableStateOf("")
    private var level by mutableStateOf(0f)

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var requestId = 0
    private val httpClient = OkHttpClient()

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** Diálogo estándar de permiso del micrófono. */
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else fail("Necesito el permiso del micrófono para escucharte.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeController.load(this)
        ThemeController.sync(this, force = true)
        initTts()

        setContent {
            AssistantScreen(
                state = state,
                heard = heard,
                reply = reply,
                level = { level },
                onMic = { onMicPressed() },
                onBack = { finish() },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ThemeController.sync(this, force = true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeController.sync(this)
    }

    /** Al salir (por ejemplo, al abrir otra app) se corta todo: nada queda escuchando en segundo plano. */
    override fun onStop() {
        stopAll()
        super.onStop()
    }

    override fun onDestroy() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // ya estaba liberado
        }
        recognizer = null
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // ya estaba parado/liberado
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ya estaba liberado
        }
        recorder = null
        recordingFile = null
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // ya estaba liberado
        }
        tts = null
        io.shutdownNow()
        super.onDestroy()
    }

    /* ---------- Botón central ---------- */

    private fun onMicPressed() {
        when (state) {
            VoiceState.Idle -> startListening()
            VoiceState.Listening -> stopListening()
            VoiceState.Thinking -> stopAll()
            VoiceState.Speaking -> stopAll()
        }
    }

    /* ---------- Voz a texto con Groq Whisper ---------- */

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                fail("No pude pedir el permiso del micrófono.")
            }
            return
        }

        heard = ""
        reply = ""
        level = 0f
        requestFocus()
        state = VoiceState.Listening

        val file = File(cacheDir, "jarvis_${System.currentTimeMillis()}.m4a")
        recordingFile = file

        try {
            val mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
        } catch (e: Exception) {
            recordingFile = null
            fail("No pude usar el micrófono para grabar audio.")
        }
    }

    private fun stopListening() {
        val activeRecorder = recorder
        val audioFile = recordingFile
        recorder = null
        recordingFile = null

        if (activeRecorder != null) {
            try {
                activeRecorder.stop()
            } catch (_: Exception) {
                // si se cancela o falla, seguimos intentando transcribir el archivo si existe
            }
            try {
                activeRecorder.release()
            } catch (_: Exception) {
                // nada
            }
        }

        if (audioFile == null || !audioFile.exists()) {
            fail("No pude grabar el audio.")
            return
        }

        state = VoiceState.Thinking
        io.execute {
            transcribeAudio(audioFile)
        }
    }

    private fun transcribeAudio(file: File) {
        if (BuildConfig.GROQ_API_KEY.isBlank()) {
            runOnUiThread { fail("Falta la clave de la API de Jarvis (ver README: GROQ_API_KEY).") }
            file.delete()
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/mp4".toMediaType()),
            )
            .addFormDataPart("model", "whisper-large-v3")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val raw = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "La clave de la API no es válida."
                    429 -> "Estoy saturado, intenta de nuevo en un momento."
                    else -> "No pude transcribir el audio (código ${response.code})."
                }
                runOnUiThread { fail(message) }
                file.delete()
                return
            }

            val text = JSONObject(raw).optString("text", "").trim()
            if (text.isBlank()) {
                runOnUiThread { fail("No te escuché bien. Inténtalo otra vez.") }
                file.delete()
                return
            }

            runOnUiThread {
                heard = text
                handleHeard(text)
            }
        } catch (e: Exception) {
            runOnUiThread { fail("No pude transcribir tu voz con Groq.") }
        } finally {
            file.delete()
        }
    }

    /* ---------- Qué hacer con lo que dijiste ---------- */

    private fun handleHeard(text: String) {
        heard = text
        val cmd = LocalCommands.match(text)
        if (cmd != null) runCommand(cmd) else askAi(text)
    }

    /** Comando local: abre la app directo, sin gastar tokens de IA. */
    private fun runCommand(cmd: Command) {
        abandonFocus()
        if (Shortcuts.open(this, cmd)) {
            reply = cmd.confirmation
            state = VoiceState.Idle
        } else {
            say("No encontré esa aplicación en el radio.")
        }
    }

    private fun askAi(text: String) {
        state = VoiceState.Thinking
        if (BuildConfig.GROQ_API_KEY.isBlank()) {
            fail("Falta la clave de la API de Jarvis (ver README: GROQ_API_KEY).")
            return
        }
        val id = ++requestId
        try {
            io.execute {
                val r = Jarvis.ask(text)
                runOnUiThread {
                    if (id == requestId && !isDestroyed) say(r.text)
                }
            }
        } catch (e: RejectedExecutionException) {
            fail("No pude enviar la consulta.")
        }
    }

    /* ---------- Texto a voz ---------- */

    private fun initTts() {
        try {
            tts = TextToSpeech(this) { status ->
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) setupTts(engine) else ttsReady = false
            }
        } catch (e: Exception) {
            ttsReady = false
        }
    }

    private fun setupTts(engine: TextToSpeech) {
        try {
            var r = engine.setLanguage(Locale.forLanguageTag(Config.IDIOMA_VOZ))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = engine.setLanguage(Locale.forLanguageTag("es"))
            }
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { finishSpeaking() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { finishSpeaking() }
                }
            })
            if (!ttsReady) {
                Toast.makeText(this, "Este radio no tiene voz en español: Jarvis responderá solo con texto", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            ttsReady = false
        }
    }

    /** Muestra la respuesta y la lee en voz alta (si el radio tiene voz sintética). */
    private fun say(text: String) {
        reply = text
        val engine = tts
        if (engine == null || !ttsReady) {
            finishSpeaking()
            return
        }
        state = VoiceState.Speaking
        try {
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis") == TextToSpeech.ERROR) finishSpeaking()
        } catch (e: Exception) {
            finishSpeaking()
        }
    }

    private fun finishSpeaking() {
        state = VoiceState.Idle
        abandonFocus()
    }

    /** Mensaje de error solo en pantalla (sin voz) y vuelta al estado inicial. */
    private fun fail(message: String) {
        reply = message
        state = VoiceState.Idle
        abandonFocus()
    }

    private fun stopAll() {
        requestId++ // ignora cualquier respuesta de IA que llegue tarde
        val activeRecorder = recorder
        recorder = null
        try {
            activeRecorder?.stop()
        } catch (e: Exception) {
            // nada
        }
        try {
            activeRecorder?.release()
        } catch (e: Exception) {
            // nada
        }
        recordingFile?.delete()
        recordingFile = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            // nada
        }
        level = 0f
        state = VoiceState.Idle
        abandonFocus()
    }

    /* ---------- Audio: la música se pausa mientras hablas y vuelve sola ---------- */

    private fun requestFocus() {
        try {
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        } catch (e: Exception) {
            // si falla, la música simplemente sigue sonando
        }
    }

    private fun abandonFocus() {
        try {
            audio.abandonAudioFocus(focusListener)
        } catch (e: Exception) {
            // nada
        }
    }
}
