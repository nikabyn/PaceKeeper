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
import org.htwk.pacing.backend.database.ModeDatabase
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.UserProfileRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * A [CoroutineWorker] responsible for running long-lived jobs separate from the user interface.
 *
 * This worker ensures continuous execution of these jobs:
 *  - [HealthConnectJob]
 *  - [EnergyPredictionJob]
 *  - [EnergyNotificationJob]
 *
 * The worker runs each job in a separate coroutine with automatic retry
 * and exponential backoff in case of failure.
 *
 * @param context Application context used for WorkManager, notifications, and job execution.
 * @param workerParams Parameters provided by WorkManager for this worker instance.
 * @param db Used by jobs for storing and reading data.
 */
class ForegroundWorker(
    private val context: Context,
    workerParams: WorkerParameters,
    private val db: PacingDatabase,
    private val modeDb: ModeDatabase,
    val userProfileRepository: UserProfileRepository
) : CoroutineWorker(context, workerParams) {
    private companion object {
        const val WORK_NAME = "ForegroundWorker"
    }

    /**
     * Main entry point for the worker when scheduled by WorkManager.
     *
     * - Sets the worker as a foreground service to avoid background restrictions.
     * - Launches long-running jobs in a [supervisorScope], each with automatic retry.
     * - Uses [launchRepeating] to apply exponential backoff on failures.
     *
     * Exceptions are caught internally in each repeating job; this method itself should not throw.
     *
     * @return [Result.retry] so that WorkManager retries the worker if it is stopped or fails.
     */
    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        supervisorScope {
            val isInDemo = modeDb.modeDao().getMode()?.demo ?: false
            if (isInDemo) {
                launchRepeating(DataGenerateJob.TAG) {
                    DataGenerateJob.run(context = applicationContext, db = db)
                }
            } else {
                launchRepeating(HealthConnectJob.TAG) {
                    HealthConnectJob.run(context = applicationContext, db = db)
                }
            }

            launchRepeating(EnergyPredictionJob.TAG) {
                EnergyPredictionJob.run(db = db)
            }
            launchRepeating(EnergyNotificationJob.TAG) {
                EnergyNotificationJob.run(
                    context = applicationContext,
                    db = db,
                    userProfileRepository
                )
            }
        }

        Log.i(WORK_NAME, "Sending retry request")
        return Result.retry()
    }

    /**
     * Launches a coroutine that repeatedly executes the given [func] until the scope is cancelled.
     *
     * - Applies exponential backoff for retries when an exception occurs.
     * - Logs failures with the job name and retry interval.
     *
     * @param name A descriptive name used in logging to identify the job.
     * @param func Suspended closure representing the work to execute repeatedly.
     */
    private fun CoroutineScope.launchRepeating(name: String, func: suspend () -> Unit) {
        val backoffMin = 10.seconds
        val backoffMax = 5.hours
        val backoffFactor = 2.0
        var backoff = backoffMin

        launch {
            while (!isStopped) {
                try {
                    func()
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
            .setContentTitle(context.getString(R.string.energy_prediction))
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
