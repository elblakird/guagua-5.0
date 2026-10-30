package com.mivehiculo.launcher

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class AssistantActivity : ComponentActivity() {

    // ⚠️ PEGA TU API KEY DE GROQ AQUÍ ENTRE LAS COMILLAS ⚠️
    private val apiKey = "AQUI_TU_API_KEY_DE_GROQ" 

    private var isRecording by mutableStateOf(false)
    private var userText by mutableStateOf("Presiona el micrófono y habla...")
    private var jarvisText by mutableStateOf("")

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }

        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Jarvis", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(userText, color = Color.Gray, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(jarvisText, color = Color(0xFFFF8C00), fontSize = 24.sp)
                    
                    Spacer(modifier = Modifier.height(40.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(if (isRecording) Color.Red else Color(0xFFFF8C00), CircleShape)
                            .clickable {
                                if (isRecording) stopRecording() else startRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎤", fontSize = 50.sp)
                    }
                }
            }
        }
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("audio", ".m4a", cacheDir)
            // this@AssistantActivity es necesario para Android 11
            mediaRecorder = MediaRecorder(this@AssistantActivity).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            userText = "Escuchando..."
            jarvisText = ""
        } catch (e: Exception) {
            Toast.makeText(this, "Error al grabar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            userText = "Procesando audio..."
            enviarAudioAGroq()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al detener: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun enviarAudioAGroq() {
        val file = audioFile ?: return

        if (apiKey == "AQUI_TU_API_KEY_DE_GROQ") {
            jarvisText = "Error: Falta la API Key en el código."
            return
        }

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/m4a".toMediaTypeOrNull()))
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", "es")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { jarvisText = "Error de red: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val textoReconocido = json.getString("text")
                    runOnUiThread { 
                        userText = "Tú: $textoReconocido"
                        enviarTextoAlChat(textoReconocido)
                    }
                } else {
                    runOnUiThread { jarvisText = "Error API: $responseBody" }
                }
            }
        })
    }

    private fun enviarTextoAlChat(texto: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("model", "llama3-8b-8192") 
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Eres Jarvis, el asistente del coche. Responde en español, de forma muy breve y directa (máximo 20 palabras).")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", texto)
                })
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { jarvisText = "Error: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val choices = json.getJSONArray("choices")
                    val respuesta = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    runOnUiThread { jarvisText = "Jarvis: $respuesta" }
                } else {
                    runOnUiThread { jarvisText = "Error IA: $responseBody" }
                }
            }
        })
    }
}
