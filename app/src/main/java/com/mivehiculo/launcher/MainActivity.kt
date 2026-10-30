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
import com.mivehiculo.launcher.AssistantActivity

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

            // Creamos las acciones reales conectadas a Android
            val actions = HomeActions(
                openRadio = { toast("Abriendo Radio (pendiente)") },
                openMusic = { openAppByPackage("com.android.music") },
                openPhone = { openAppByPackage("com.android.dialer") },
                openApps = { 
                    val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALCULATOR) }
                    try { startActivity(intent) } catch(e: Exception) { toast("Sin App Drawer") }
                },
                openAndroidAuto = { toast("Abriendo Android Auto (pendiente)") },
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
                    catch(e: Exception) { toast("Jarvis no instalado") }
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

    // Función para abrir apps buscando su paquete
    private fun openAppByPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            toast("App no encontrada en esta radio")
        }
    }

    // Función auxiliar para los Toasts
    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
