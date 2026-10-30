package com.mivehiculo.launcher

import android.os.Bundle
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
                openRadio = {}, openMusic = {}, openPhone = {}, openApps = {},
                openAndroidAuto = {}, openNavigation = {}, openSettings = {},
                mediaPrev = {}, mediaPlayPause = {}, mediaNext = {},
                openNotificationAccess = {}, openMaintenance = {},
                openRearCamera = {}, pickRearCamera = {}, pickRadio = {}, openAssistant = {}
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
}
