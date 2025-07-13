package org.htwk.pacing.backend

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFunctionsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testCreateNotificationChannel_createsChannelOnApi26Plus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Remove channel if exists before test
            notificationManager.deleteNotificationChannel(Notification_Channel_ID)

            createNotificationChannel(context)

            val channel = notificationManager.getNotificationChannel(Notification_Channel_ID)
            assertNotNull("Notification channel should be created", channel)
            assertEquals("Energy Notification Channel", channel?.name)
        }
    }

    @Test
    fun testShowNotification_setsNotificationShownFlag() {
        // Clear the flag first
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_shown", false).apply()

        showNotification(context)

        val flag = prefs.getBoolean("notification_shown", false)
        assertTrue("Notification shown flag should be true after showing notification", flag)
    }

    @Test
    fun testShowNotification_doesNotCrashWithoutPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // This test assumes notification permission is denied or granted.
            // Just call showNotification and check it doesn't crash.
            showNotification(context)
        }
    }
}
