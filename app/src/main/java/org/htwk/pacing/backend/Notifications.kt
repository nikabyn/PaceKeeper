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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.datetime.Clock
import org.htwk.pacing.MainActivity
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

const val NOTIFICATION_CHANNEL_ID = "Energy_Notification_ID"
const val ENERGY_WARNING_NOTIFICATION_ID = 2

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
    val channelId = NOTIFICATION_CHANNEL_ID
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
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    prefs.edit { putBoolean("notification_shown", true) }
    Log.d("Notification", "notification_shown Flag gesetzt: true")

    if (!permissionGranted) {
        Log.d("Notification", "Permission fehlt – Notification wird nicht gezeigt")
        return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        action = "OPENED_FROM_NOTIFICATION"
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("WARNING!")
        .setContentText(context.getString(R.string.energy_warning_text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setSmallIcon(R.drawable.rounded_show_chart_24)
        .setAutoCancel(true)

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.notify(ENERGY_WARNING_NOTIFICATION_ID, builder.build())

    Log.d("Notification", "Notification wurde ausgelöst")
}


class NotificationsBackgroundWorker(
    context: Context,
    workerParams: WorkerParameters,
    predictedEnergyLevelDao: PredictedEnergyLevelDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs =
            applicationContext.getSharedPreferences("energy_prefs", Context.MODE_PRIVATE)

        val now = Clock.System.now()

        //get the minimum energy prediction of a future time window for the limit check
        val energyLevelDataWindow: List<PredictedEnergyLevelEntry> =
            predictedEnergyLevelDao.getInRange(now + 3.hours, now + 4.hours)
        val predictedEnergyLevel =
            energyLevelDataWindow.minBy { it.percentage.toDouble() }.percentage.toDouble()

        Log.d("NotificationsBackgroundWorker", "Energy level is $predictedEnergyLevel")

        if (predictedEnergyLevel < 0.2f) {
            Log.d("NotificationsBackgroundWorker", "Energy is low, showing notification")
            showNotification(applicationContext)
        } else {
            Log.d("NotificationsBackgroundWorker", "Energy is sufficient, no notification")
        }
        return Result.success()
    }
}

fun scheduleEnergyCheckWorker(context: Context) {
    val workRequest =
        PeriodicWorkRequestBuilder<NotificationsBackgroundWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "EnergyCheckWorker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
