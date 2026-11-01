package com.mivehiculo.launcher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MaintenanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("mantenimiento_prefs", Context.MODE_PRIVATE)
        
        setContent {
            var lastOilChangeKm by remember { mutableStateOf(prefs.getInt("last_oil_km", 0).toString()) }
            var currentKm by remember { mutableStateOf(prefs.getInt("current_km", 0).toString()) }
            var intervalKm by remember { mutableStateOf(prefs.getInt("interval_km", 5000).toString()) }
            var savedMessage by remember { mutableStateOf("") }

            Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Control de Mantenimiento", color = Color.White, fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(40.dp))

                    OutlinedTextField(
                        value = currentKm,
                        onValueChange = { currentKm = it },
                        label = { Text("Kilometraje Actual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = lastOilChangeKm,
                        onValueChange = { lastOilChangeKm = it },
                        label = { Text("Km del último cambio de aceite") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = intervalKm,
                        onValueChange = { intervalKm = it },
                        label = { Text("Intervalo de cambio (ej. 5000)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(30.dp))

                    Button(
                        onClick = {
                            val kmActual = currentKm.toIntOrNull() ?: 0
                            val kmUltimo = lastOilChangeKm.toIntOrNull() ?: 0
                            val intervalo = intervalKm.toIntOrNull() ?: 5000

                            prefs.edit().apply {
                                putInt("current_km", kmActual)
                                putInt("last_oil_km", kmUltimo)
                                putInt("interval_km", intervalo)
                                apply()
                            }

                            val restantes = (kmUltimo + intervalo) - kmActual
                            savedMessage = if (restantes > 0) {
                                "Guardado. Faltan $restantes km para el próximo cambio."
                            } else {
                                "¡Ya toca cambio de aceite!"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar Datos")
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    if (savedMessage.isNotEmpty()) {
                        Text(savedMessage, color = Color(0xFFFF8C00), fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver al Inicio")
                    }
                }
            }
        }
    }
}
