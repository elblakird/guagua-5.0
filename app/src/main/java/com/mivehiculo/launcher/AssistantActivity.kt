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

        if (apiKey == "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") {
            jarvisText = "Error: Falta la API Key en el código."
            return
        }

        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/m4a".toMediaTypeOrNull()))
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", "es") // FORZAMOS ESPAÑOL
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
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Eres Jarvis, el asistente del coche. Responde en español, de forma muy breve y directa (máximo 20 palabras) para no distraer al conductor.")
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
