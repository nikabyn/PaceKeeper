package org.htwk.pacing.ui.components

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.htwk.pacing.MainActivity
import org.htwk.pacing.R

fun showNotification(context: Context) {
    val channelId = "my_channel_id"

    // ✅ Only check permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        // Permission is not granted – do NOT show notification
        return
    }

    // Intent to open MainActivity when the notification is tapped
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setContentTitle("My notification")
        .setContentText("This is a push notification.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setSmallIcon(R.drawable.ic_launcher_background)
        .setAutoCancel(true)


    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.notify(1, builder.build())


}
