package org.htwk.pacing.backend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.htwk.pacing.MainActivity
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object NotificationIds {
    const val FOREGROUND_CHANNEL_ID = "foreground_ch"
    const val FOREGROUND_NOTIFICATION_ID = 1

    const val ENERGY_WARNING_CHANNEL_ID = "energy_low_ch"
    const val ENERGY_WARNING_NOTIFICATION_ID = 2
}

fun initNotificationSystem(activity: ComponentActivity) {
    val sharedPrefs = activity.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        sharedPrefs.edit { putBoolean("notifications_allowed", isGranted) }
        if (!isGranted) {
            Toast.makeText(activity, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        //No runtime permission needed before API 33
        sharedPrefs.edit { putBoolean("notifications_allowed", true) }
    } else {
        //Only reference the constant if API >= 33
        val permissionGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            sharedPrefs.edit { putBoolean("notifications_allowed", true) }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun createNotificationChannel(context: Context) {
    // Create channel ONLY on API 26+
    val channelId = NotificationIds.ENERGY_WARNING_CHANNEL_ID
    val channelName = "Energy Notification Channel"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(channelId, channelName, importance).apply {
        description =
            "Sends a Push-Notification if the Energy level falls below a certain Percentage"
    }

    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

fun showNotification(context: Context) {
    createNotificationChannel(context)

    val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    prefs.edit { putBoolean("notification_shown", true) }
    Log.d("Notification", "set notification_shown: true")

    if (!permissionGranted) {
        Log.d("Notification", "Permission missing – Notification not sent")
        return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        action = "OPENED_FROM_NOTIFICATION"
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, NotificationIds.ENERGY_WARNING_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.warning))
        .setContentText(context.getString(R.string.energy_warning_text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setSmallIcon(R.drawable.rounded_show_chart_24)
        .setAutoCancel(true)

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.notify(NotificationIds.ENERGY_WARNING_NOTIFICATION_ID, builder.build())

    Log.d("Notification", "Notification sent")
}

suspend fun getRelevantPredictedEnergyLevel(predictedEnergyLevelDao: PredictedEnergyLevelDao): Double? {
    val now = Clock.System.now()
    val energyLevelDataWindow: List<PredictedEnergyLevelEntry> =
        predictedEnergyLevelDao.getInRange(now, now + 6.hours)
    val minimumEntry =
        energyLevelDataWindow.minByOrNull { it.percentage.toDouble() }

    return minimumEntry?.percentage?.toDouble()
}

suspend fun checkAndNotifyEnergy(
    context: Context,
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
) {
    while (true) {
        val predictedEnergy = getRelevantPredictedEnergyLevel(predictedEnergyLevelDao)
            ?: 1.0 // if no data available, assume energy is ok and thus display no warning

        Log.d(
            "NotificationsJob",
            "Predicted Energy level of %.2f".format(predictedEnergy)
        )

        if (predictedEnergy < 0.2) {
            Log.d("NotificationsJob", "Energy is low, showing notification")
            showNotification(context)
        } else {
            Log.d("NotificationsJob", "Energy is sufficient, no notification")
        }

        delay(1.minutes)
    }
}