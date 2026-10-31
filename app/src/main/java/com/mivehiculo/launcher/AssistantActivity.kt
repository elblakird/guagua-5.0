package com.mivehiculo.launcher

import android.Manifest
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
import androidx.compose.foundation.shape.RoundedCornerShape
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

    // ⚠️ PEGA TU API KEY DE GROQ AQUÍ ⚠️
    private val apiKey = "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" 

    private var isRecording by mutableStateOf(false)
    private var userText by mutableStateOf("Aquí verás lo que dices")
    private var jarvisText by mutableStateOf("")

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        setContent {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Jarvis", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(30.dp))

                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF333333), RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Column {
                            Text("Tú", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(userText, color = Color.White, fontSize = 18.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (jarvisText.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF2A1B10), RoundedCornerShape(16.dp)).padding(16.dp)) {
                            Column {
                                Text("Jarvis", color = Color(0xFFFF8C00), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(jarvisText, color = Color.White, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(if (isRecording) Color.Red else Color(0xFFFF8C00), CircleShape)
                            .clickable {
                                if (isRecording) stopRecording() else startRecording()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎤", fontSize = 40.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isRecording) "Escuchando..." else "Presiona para hablar", color = Color.Gray)
                }
            }
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "¡Falta el permiso de micrófono!", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        try {
            audioFile = File.createTempFile("audio", ".m4a", cacheDir)
            mediaRecorder = MediaRecorder().apply {
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
            Toast.makeText(this, "Error al grabar. Micrófono no disponible.", Toast.LENGTH_LONG).show()
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            userText = "Procesando..."
            enviarAudioAGroq()
        } catch (e: Exception) {
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
                    val textoReconocido = JSONObject(responseBody).getString("text")
                    runOnUiThread { 
                        userText = textoReconocido
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
            // MODELO ACTUALIZADO Y COMPATIBLE CON GROQ
            put("model", "openai/gpt-oss-20b") 
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
                    val respuesta = JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    runOnUiThread { jarvisText = respuesta }
                } else {
                    runOnUiThread { jarvisText = "Error IA: $responseBody" }
                }
            }
        })
    }
}
