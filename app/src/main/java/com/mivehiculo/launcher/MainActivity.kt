package com.mivehiculo.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.mivehiculo.launcher.data.MediaState
import com.mivehiculo.launcher.ui.HomeActions
import com.mivehiculo.launcher.ui.HomeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault())
            
            var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }
            var currentDate by remember { mutableStateOf(dateFormat.format(Date())) }

            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = timeFormat.format(Date())
                    currentDate = dateFormat.format(Date())
                    kotlinx.coroutines.delay(60000L)
                }
            }

            val actions = HomeActions(
                openRadio = { openAppByPackage("com.jancar.radio") },
                openMusic = { openAppByPackage("com.jancar.music") },
                openPhone = { 
                    val intent = Intent(Intent.ACTION_DIAL)
                    try { startActivity(intent) } catch(e: Exception) { toast("No se pudo abrir el teléfono") }
                },
                openApps = { toast("Aquí iría el cajón de apps") }, // El cajón de apps requiere otra configuración
                openAndroidAuto = { openAppByPackage("com.google.android.projection.sink") },
                openNavigation = { openAppByPackage("com.google.android.apps.maps") },
                openSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                mediaPrev = { toast("Anterior") },
                mediaPlayPause = { toast("Play/Pausa") },
                mediaNext = { toast("Siguiente") },
                openNotificationAccess = { toast("Permiso de Notificaciones") },
                openMaintenance = { toast("Abriendo Mantenimiento") },
                openRearCamera = { toast("Abriendo Cámara") },
                pickRearCamera = { toast("Seleccionar Cámara") },
                pickRadio = { toast("Seleccionar Radio") },
                openAssistant = { 
                    try { startActivity(Intent(this@MainActivity, AssistantActivity::class.java)) } 
                    catch(e: Exception) { toast("Error al abrir Jarvis") }
                }
            )

            HomeScreen(
                time = currentTime,
                date = currentDate,
                weather = "24°C",
                feels = "Despejado",
                media = MediaState(), 
                odometerKm = "0", 
                actions = actions
            )
        }
    }

    // Función para abrir apps usando su nombre de paquete
    private fun openAppByPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            toast("App no encontrada: $packageName")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
