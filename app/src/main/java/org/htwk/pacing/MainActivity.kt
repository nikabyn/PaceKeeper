package org.htwk.pacing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.htwk.pacing.backend.initNotificationSystem
import org.htwk.pacing.backend.scheduleEnergyCheckWorker
import org.htwk.pacing.ui.Main
import org.htwk.pacing.ui.components.createNotificationChannel
import org.htwk.pacing.ui.components.showNotification
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initNotificationSystem(this)
        scheduleEnergyCheckWorker(this)
        setContent {
            Main()
        }
    }


}

