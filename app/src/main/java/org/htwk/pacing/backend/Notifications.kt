package org.htwk.pacing.backend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.htwk.pacing.MainActivity
import org.htwk.pacing.R

//This is the only function you call from MainActivity
fun initNotificationSystem(activity: ComponentActivity) {
    val sharedPrefs: SharedPreferences =
        activity.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    val alreadyRequested = sharedPrefs.contains("notifications_allowed")
    val allowedBefore = sharedPrefs.getBoolean("notifications_allowed", false)

    val permissionGranted = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED


    //Define the launcher INSIDE the backend
    val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        sharedPrefs.edit().putBoolean("notifications_allowed", isGranted).apply()
        if (isGranted) {
            showNotification(activity)
        } else {
            Toast.makeText(activity, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    //Permission logic
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionGranted) {
        sharedPrefs.edit().putBoolean("notifications_allowed", true).apply()
        showNotification(activity)
    } else {
        if (!alreadyRequested || !allowedBefore) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d("notification", "Permission previously denied, asking again...")
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// Create Notification
fun createNotificationChannel(context: Context) {
    val channelId = "my_channel_id"
    val channelName = "My Channel"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(channelId, channelName, importance).apply {
        description = "My notification channel description"
    }

    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

//Shows Notification
fun showNotification(context: Context) {
    createNotificationChannel(context)

    val channelId = "my_channel_id"

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.d("notification", "Permissions for notifications not granted")
        return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setContentTitle("WARNING!")
        .setContentText("Your Energy level is below 20%. Please Rest immediately")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setSmallIcon(R.drawable.ic_launcher_background)
        .setAutoCancel(true)

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.notify(2, builder.build())

    Log.d("NotificationTest", "showNotification() called")

}


class MyBackgroundWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences("energy_prefs", Context.MODE_PRIVATE)
        val energy = prefs.getInt("energy", 100) // Default: 100%
        Log.d("MyBackgroundWorker", "Energy level is $energy")

        if (energy < 20) {
            Log.d("MyBackgroundWorker", "Energy is low, showing notification")
            showNotification(applicationContext)
        } else {
            Log.d("MyBackgroundWorker", "Energy is sufficient, no notification")
        }

        return Result.success()
    }

}