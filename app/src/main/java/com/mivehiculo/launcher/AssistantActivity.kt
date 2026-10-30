package com.mivehiculo.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
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
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Jarvis: presionar para hablar (sin escucha continua).
 * Voz -> texto (SpeechRecognizer) -> comando local o IA -> texto -> voz (TextToSpeech).
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

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var requestId = 0

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
            VoiceState.Listening -> try {
                recognizer?.stopListening() // termina de grabar y procesa lo dicho
            } catch (e: Exception) {
                stopAll()
            }
            VoiceState.Thinking -> stopAll() // cancela la consulta
            VoiceState.Speaking -> stopAll() // calla a Jarvis
        }
    }

    /* ---------- Voz a texto ---------- */

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Exception) {
                fail("No pude pedir el permiso del micrófono.")
            }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("Este radio no tiene reconocimiento de voz. Instala o activa la app «Google» (servicios de voz).")
            return
        }
        val r = recognizer ?: createRecognizer()
        if (r == null) {
            fail("No pude iniciar el reconocimiento de voz.")
            return
        }
        heard = ""
        reply = ""
        level = 0f
        requestFocus() // pausa la música mientras hablas
        state = VoiceState.Listening
        try {
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Config.IDIOMA_VOZ)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
            )
        } catch (e: Exception) {
            fail("No pude usar el micrófono.")
        }
    }

    private fun createRecognizer(): SpeechRecognizer? = try {
        SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
    } catch (e: Exception) {
        null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        /** Volumen de la voz: mueve las ondas del botón (suavizado). */
        override fun onRmsChanged(rmsdB: Float) {
            level = level * 0.6f + ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) * 0.4f
        }

        override fun onEndOfSpeech() {
            if (state == VoiceState.Listening) state = VoiceState.Thinking
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) heard = text
        }

        override fun onResults(results: Bundle?) {
            level = 0f
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) onSttError(SpeechRecognizer.ERROR_NO_MATCH) else handleHeard(text)
        }

        override fun onError(error: Int) {
            onSttError(error)
        }
    }

    private fun onSttError(code: Int) {
        level = 0f
        if (code == SpeechRecognizer.ERROR_CLIENT) { // ocurre al cancelar nosotros mismos
            state = VoiceState.Idle
            abandonFocus()
            return
        }
        if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                // nada
            }
            recognizer = null // la próxima vez se crea uno nuevo
        }
        fail(
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No te escuché bien. Toca el micrófono e inténtalo de nuevo."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "No hay internet para reconocer la voz."
                SpeechRecognizer.ERROR_AUDIO -> "No pude usar el micrófono del radio."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del micrófono."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor estaba ocupado. Inténtalo otra vez."
                else -> "No pude reconocer la voz (código $code)."
            }
        )
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
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // nada
        }
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
