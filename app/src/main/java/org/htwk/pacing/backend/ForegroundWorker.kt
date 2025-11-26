package org.htwk.pacing.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.htwk.pacing.R
import org.htwk.pacing.backend.NotificationIds.FOREGROUND_CHANNEL_ID
import org.htwk.pacing.backend.NotificationIds.FOREGROUND_NOTIFICATION_ID
import org.htwk.pacing.backend.data_collection.health_connect.syncWithHealthConnect
import org.htwk.pacing.backend.database.PacingDatabase

class ForegroundWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val db: PacingDatabase,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val WORK_NAME = "ForegroundWorker"
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        coroutineScope {
            try {
                select<Unit> {
                    launch { syncWithHealthConnect(applicationContext, db) }.onJoin
                    launch {
                        checkAndNotifyEnergy(applicationContext, db.predictedEnergyLevelDao())
                    }.onJoin
                }
                Log.e(WORK_NAME, "At least one child completed.")
            } catch (e: Exception) {
                Log.e(WORK_NAME, "Error in foreground worker:", e)
            }
        }

        Log.i(WORK_NAME, "Sending retry request")
        return Result.retry()
    }

    private fun createNotification(): Notification {
        val channel =
            NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "PaceKeeper Channel",
                NotificationManager.IMPORTANCE_LOW
            )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return NotificationCompat.Builder(applicationContext, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Predicting your energy")
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                createNotification(),
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, createNotification())
        }
    }
}
