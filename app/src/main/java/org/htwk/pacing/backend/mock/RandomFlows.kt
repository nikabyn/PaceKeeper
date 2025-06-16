package org.htwk.pacing.backend.mock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import kotlin.random.Random

/**
 * Generates heart rate values with a random delay between each of them.
 *
 * @param averageDelayMs delays are in the range of this value
 */
fun randomHeartRate(averageDelayMs: Int): Flow<Pair<Double, Instant>> = flow {
    while (true) {
        val value = Random.nextDouble(55.0, 107.0)
        emit(Pair(value, Clock.System.now()))
        val millis = Random.nextDouble(averageDelayMs * 0.5, averageDelayMs * 1.5)
        delay(millis.toLong())
    }
}

class RandomHeartRateWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val heartRateDao: HeartRateDao
) : CoroutineWorker(context, workerParams) {
    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun doWork(): Result {
        setForeground(ForegroundInfo(1, createNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC))

        // TODO: Figure out how to properly reset db
        heartRateDao.deleteAllEntries()

        randomHeartRate(100).collect { (value, time) ->
            heartRateDao.insert(HeartRateEntry(value, time))
        }
        return Result.success()
    }

    private fun createNotification(): Notification {
        val channelId = "heart_rate_ch"
        val channel =
            NotificationChannel(
                channelId,
                "Heart Rate Generation",
                NotificationManager.IMPORTANCE_LOW
            )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Generating heart data…")
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .build()
    }
}
