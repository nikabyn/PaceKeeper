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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.htwk.pacing.R
import org.htwk.pacing.backend.NotificationIds.FOREGROUND_CHANNEL_ID
import org.htwk.pacing.backend.NotificationIds.FOREGROUND_NOTIFICATION_ID
import org.htwk.pacing.backend.data_collection.health_connect.syncWithHealthConnect
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

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

        supervisorScope {
            launchRepeating("HealthConnectJob") { syncWithHealthConnect(applicationContext, db) }
            launchRepeating("EnergyPredictionJob") { Predictor.predictAndStoreEnergy(db) }
            launchRepeating("EnergyNotificationsJob") {
                checkAndNotifyEnergy(applicationContext, db.predictedEnergyLevelDao())
            }
        }

        Log.i(WORK_NAME, "Sending retry request")
        return Result.retry()
    }

    private fun CoroutineScope.launchRepeating(name: String, block: suspend () -> Unit) {
        val backoffMin = 10.seconds
        val backoffMax = 5.hours
        val backoffFactor = 2.0
        var backoff = backoffMin

        launch {
            while (!isStopped) {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e(WORK_NAME, "$name failed. Retrying in $backoff...", e)
                    delay(backoff)

                    backoff *= backoffFactor
                    if (backoff >= backoffMax) {
                        backoff = backoffMax
                    }
                }
            }
        }
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
