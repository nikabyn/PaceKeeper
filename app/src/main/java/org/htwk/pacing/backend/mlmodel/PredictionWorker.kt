package org.htwk.pacing.backend.mlmodel

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
import androidx.work.
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.PredictedHeartRateDao
import org.htwk.pacing.backend.database.PredictedHeartRateEntry
import org.htwk.pacing.backend.heuristics.energyLevelFromHeartRate
import org.htwk.pacing.ui.math.roundInstantToResolution
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class PredictionWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val mlModel: MLModel,
    private val heartRateDao: HeartRateDao,
    private val predictedHeartRateDao: PredictedHeartRateDao,
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao
) : CoroutineWorker(context, workerParams) {

    //higher-order drop-in function for energy-> hr calculation
    private val energyHeuristic: (Double) -> Percentage = { hr: Double ->
        Percentage(energyLevelFromHeartRate(hr))
    }

    companion object {
        const val WORK_NAME = "PredictionWorker" // For unique work

        //TODO: extract notification channel ids and notification ids to external constants
        // Notification Channel and ID for the Foreground Service
        private const val FOREGROUND_CHANNEL_ID = "prediction_execution_foreground_ch"
        private const val FOREGROUND_NOTIFICATION_ID =
            101 // Unique for this worker's FG notification

        // Notification Channel and ID for Prediction Alerts
        private const val ALERT_CHANNEL_ID = "prediction_alert_channel"
        private const val ALERT_NOTIFICATION_ID_BASE =
            200 // Base ID for alerts, can increment if needed

        // Prediction constants
        private const val WARNING_TRIGGER_THRESHOLD = 0.3 // Example threshold
    }

    /**
     * Prepares the input data for the machine learning model.
     *
     * This function takes a list of heart rate entries and a target timestamp rounded to the
     * last 10 minute mark (`now10min`) and transforms it into a `FloatArray` of 10-minute-averages
     * suitable for the ML model.
     *
     * Steps:
     * 1. **Define Input Window:** Determine the start time for the input data based on `now10min`
     *    and the model's expected input duration (`MLModel::INPUT_DAYS`).
     * 2. **Group by Time Interval:** Group the provided `heartRateData` into 10-minute intervals.
     * 3. **Calculate Averages:** For each 10-minute interval, calculate the average heart rate.
     * 4. **Populate Input Array:** Create a `FloatArray` of the size expected by the model (`MLModel::INPUT_SIZE`).
     *    Iterate through the 10-minute intervals from the `modelInputBeginInstant`.
     *    - If an average heart rate is available for the current interval, use it.
     *    - Otherwise, use the last known average heart rate (extrapolating from the most recent valid data).
     * 5. **Handle Empty Data:** If there's no heart rate data available within the input window,
     *    return an array filled with zeros.
     *
     * @param heartRateData A list of [HeartRateEntry] objects containing historical heart rate measurements.
     * @param now10min The current time, rounded to the nearest 10-minute interval, serving as the
     *                 reference point for creating the model input window.
     * @return A `FloatArray` representing the prepared input data for the ML model.
     */
    private fun prepareModelInput(
        heartRateData: List<HeartRateEntry>,
        now10min: kotlinx.datetime.Instant
    ): FloatArray {
        val modelInputSize = MLModel::INPUT_SIZE.get().toInt()
        val modelInputDuration = (MLModel::INPUT_DAYS.get().toInt()).days
        val averageArray = FloatArray(modelInputSize)

        val modelInputBeginInstant = now10min - modelInputDuration

        //sort into buckets, where the key is the 10-minute interval each entry falls into
        val groupedBy10Min = heartRateData.groupBy { hrEntry ->
            roundInstantToResolution(hrEntry.time, 10.minutes)
        }

        // TODO: weighted resampling/average, because incoming HR data points are probably unevenly spaced
        val averagesPer10Min = groupedBy10Min.mapValues { (_, group) ->
            group.map { hrEntry -> hrEntry.bpm.toFloat() }.average().toFloat()
        }

        /*check if there is at least one valid 10 min group, to extrapolate from first 10 min value
        to the left*/
        val leftMostTime = averagesPer10Min.minByOrNull { it.key }
        if (leftMostTime == null) return FloatArray(modelInputSize) //if empty, return zero-data

        var lastKnownAverage = averagesPer10Min[leftMostTime.key]!!
        for (i in 0 until modelInputSize) {
            //if there's an average available for this 10 minute interval, use it from now on
            val currentAverage = averagesPer10Min[modelInputBeginInstant + (i * 10).minutes]

            if (currentAverage != null) lastKnownAverage = currentAverage

            averageArray[i] = lastKnownAverage
        }

        return averageArray
    }

    private suspend fun updateDBWithPredictionOutput(
        predictionOutput: FloatArray,
        now10min: kotlinx.datetime.Instant
    ) {
        //delete previous HR and Energy Level prediction
        predictedHeartRateDao.deleteAll()
        predictedEnergyLevelDao.deleteAll()

        //store predictions in database
        predictedHeartRateDao.insertMany(
            List(predictionOutput.size) { i ->
                PredictedHeartRateEntry(
                    now10min + 10.minutes * i,
                    predictionOutput[i].toLong()
                )
            }
        )

        predictedEnergyLevelDao.insertMany(
            List(predictionOutput.size) { i ->
                PredictedEnergyLevelEntry(
                    now10min + 10.minutes * i,
                    energyHeuristic(predictionOutput[i].toDouble())
                )
            }
        )
    }

    private fun energyExceededCheck(predictionOutput: FloatArray) {
        //get value in the middle of predictions as relevant value for warning trigger
        //TODO: rethink after MVP
        val futureHeartRateGlimpse = predictionOutput[predictionOutput.size / 2].toDouble();
        val futureEnergyGlimpse = energyHeuristic(futureHeartRateGlimpse).toDouble()

        //trigger warning notification if energy is predicted to fall below threshold
        if (futureEnergyGlimpse < WARNING_TRIGGER_THRESHOLD) {
            sendPredictionAlertNotification(
                "Prediction Alert",
                "Model prediction ("
                        + "%.2f".format(futureEnergyGlimpse)
                        + ") fell below threshold ($WARNING_TRIGGER_THRESHOLD)."
            )
        }
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        try {
            while (true) {
                //TODO: should we consider data that came in after the most recent 10 minute mark?
                val now10min = roundInstantToResolution(Clock.System.now(), 10.minutes) + 10.minutes

                val timeSortedHeartRateData =
                    heartRateDao.getInRange(now10min - MLModel::INPUT_DAYS.get().days, now10min)
                        .sortedBy { it.time }
                if (timeSortedHeartRateData.isEmpty()) {
                    delay(1000)
                    continue
                }

                val modelInput = prepareModelInput(timeSortedHeartRateData, now10min)

                val predictionOutput = mlModel.predict(
                    testHeartRateData, now10min.toJavaInstant()
                )

                updateDBWithPredictionOutput(predictionOutput, now10min)

                energyExceededCheck(predictionOutput)

                delay(1000)
            }
        } catch (e: Exception) {
            Log.e(WORK_NAME, "Error in prediction worker execution loop", e)
            // If an unrecoverable error occurs, return failure. WorkManager might retry based on policy.
            return Result.failure()
        } finally {
            // Clean up resources if necessary.
            // This block executes whether the loop finishes normally (due to cancellation) or due to an exception.
            Log.i(WORK_NAME, "$WORK_NAME is stopping.")
        }

        //TODO: retry logic should be removed later, when all workers are merged
        //try to restart if an exception caused a loop exit
        return Result.retry()
    }

    private fun createNotification(): Notification {
        val channelId = FOREGROUND_CHANNEL_ID
        val channel =
            NotificationChannel(
                channelId,
                "Energy Level Prediction",
                NotificationManager.IMPORTANCE_LOW
            )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Predicting Energy Level…")
            .setSmallIcon(R.drawable.rounded_show_chart_24)
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

    private fun sendPredictionAlertNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Model Prediction Alerts",
            NotificationManager.IMPORTANCE_HIGH // High importance for alerts
        )
        channel.description = "Shows alerts when model predictions cross defined thresholds."
        notificationManager.createNotificationChannel(channel)

        // Using a unique ID for each alert or a fixed one if you want to overwrite
        val alertNotificationId =
            ALERT_NOTIFICATION_ID_BASE // Or System.currentTimeMillis().toInt() for unique
        val notification = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismiss notification on tap
            // .setContentIntent(pendingIntent) // Optional: What to do when notification is tapped
            .build()

        notificationManager.notify(alertNotificationId, notification)
    }
}
