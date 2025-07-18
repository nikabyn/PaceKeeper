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
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.PredictedHeartRateDao
import org.htwk.pacing.backend.database.PredictedHeartRateEntry
import org.htwk.pacing.backend.heuristics.EnergyFromHeartRateCalculator.nextEnergyLevelFromHeartRate
import org.htwk.pacing.backend.heuristics.MainEnergyHeuristicCalculator.mainEnergyHeuristic
import org.htwk.pacing.ui.math.roundInstantToResolution
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PredictionWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val mlModel: MLModel,
    private val heartRateDao: HeartRateDao,
    private val predictedHeartRateDao: PredictedHeartRateDao,
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val manualSymptonDao: ManualSymptomDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "PredictionWorker" // For unique work

        //TODO: extract notification channel ids and notification ids to external constants
        // Notification Channel and ID for the Foreground Service
        private const val FOREGROUND_CHANNEL_ID = "prediction_execution_foreground_ch"
        private const val FOREGROUND_NOTIFICATION_ID =
            201 // Unique for this worker's FG notification

        //how long to delay before running worker main loop again
        private const val REFRESH_INTERVAL_MS: Long = 1 * 5000
    }

    /**
     * Prepares the input data for the machine learning unter die Schwelle vonmodel.
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
        now10min: kotlinx.datetime.Instant,
    ): Pair<FloatArray, BooleanArray> {
        val modelInputSize = MLModel::INPUT_SIZE.get().toInt()
        val modelInputDuration = (MLModel::INPUT_DAYS.get().toInt()).days

        val averageArray = FloatArray(modelInputSize)
        val hasDataArray = BooleanArray(modelInputSize) { x -> false }

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

        //if empty, return zero-data
        if (leftMostTime == null) {
            return Pair(
                FloatArray(modelInputSize),
                hasDataArray
            )
        }

        var lastKnownAverage = averagesPer10Min[leftMostTime.key]!!
        for (i in 0 until modelInputSize) {
            //if there's an average available for this 10 minute interval, use it from now on
            val currentAverage = averagesPer10Min[modelInputBeginInstant + (i * 10).minutes]

            if (currentAverage != null) {
                lastKnownAverage = currentAverage
                hasDataArray[i] = true
            }

            averageArray[i] = lastKnownAverage
        }

        return Pair(averageArray, hasDataArray)
    }

    /**
     * Updates database with ml model prediction.
     *
     * This function takes the raw prediction output from the model (`predictionOutput`),
     * which is expected to be a `FloatArray` of predicted heart rates, and the reference
     * timestamp (`now10min`) indicating the start of the prediction window.
     *
     * **Step-by-Step:**
     * 1. **Delete Previous Predictions:** Clear out previous predictions in DB
     * 2. **Store Predicted Heart Rates and Energy levels:** Store predicted heart rate and energy level. Energy level is based on the predicted heart rate, using energyHeuristic.
     *
     * @param predictionOutputHR A `FloatArray` containing the predicted HR values to be stored
     * @param now10min The timestamp of the start of the prediction window, rounded to last 10-minute mark.
     * @see energyHeuristic
     */
    private suspend fun updateDBWithPredictionOutput(
        predictionInputHR: FloatArray,
        predictionOutputHR: FloatArray,
        now10min: kotlinx.datetime.Instant
    ) {
        //append predictionOutput to previousHeartRates, run energy calculation on past + future
        val totalHRSeries = predictionInputHR + predictionOutputHR

        /*TODO: turn PredictedEnergyLevel into EnergyLevel again
         *  write both future and past energy
         *  start heuristic at now - 12 hours, using the bpm at that point as a starting point
         *  alternatively, just use the first existing datapoint and then overwrite
         *  -> if no data exists in the db yet, overwrite with constant 0.5
         *  -> this will then be the starting point of the above process
         *  OR: just start at 1.0 for MVP
         *  .
         *  SOLUTION FOR NOW: estimate energy based on first HR value
        * */

        val initialRounds = 10
        //inital energy estimation
        val firstHR = predictionInputHR.first().toDouble()
        var energyBegin = 1.0
        for (i in 0 until initialRounds) {
            energyBegin = nextEnergyLevelFromHeartRate(energyBegin, firstHR)
        }

        val predictedEnergy = mainEnergyHeuristic(
            timeBegin = now10min - 6.hours,
            energyBegin = energyBegin,
            heartRate10MinSeries = totalHRSeries,
            symptomsFromLast3Days = manualSymptonDao.getInRange(now10min - 3.days, now10min)
        )

        //delete previous HR and Energy Level prediction
        predictedHeartRateDao.deleteAll()
        predictedEnergyLevelDao.deleteAll()

        //store predictions in database
        predictedHeartRateDao.insertMany(
            List(predictionOutputHR.size) { i ->
                PredictedHeartRateEntry(
                    now10min + 10.minutes * i,
                    predictionOutputHR[i].toLong()
                )
            }
        )

        predictedEnergyLevelDao.insertMany(
            List(predictedEnergy.size) { i ->
                PredictedEnergyLevelEntry(
                    now10min + 10.minutes * i - 6.hours,
                    Percentage(predictedEnergy[i].toDouble())
                )
            }
        )
    }

    /**
     * Overwrites Heart Rate in DB with some test data
     *
     * use for quick visual test without loading external data into db, preferably not in production
     *
     * @param offset offset into testHeartRateData (is bound-checked in there)
     */
    private suspend fun insertTestDataIntoDB(offset: Int) {
        val testHeartRateData = getTestHeartRateData(offset)

        heartRateDao.deleteAll()

        //insert testHeartRateData into db
        val listToDB = List(testHeartRateData.size) { i ->
            HeartRateEntry(
                time = (Clock.System.now() - (10.minutes * testHeartRateData.size) + 10.minutes * i),
                bpm = testHeartRateData[i].toLong()
            )
        }.filter { it.time.epochSeconds % 4500L < 900 }

        heartRateDao.insertMany(listToDB)
    }

    /**
     * Main loop for the worker, continuously fetching data, making predictions, and updating the DB.
     */
    private suspend fun workerMainLoop() {
        while (true) {
            //TODO: should we consider data that came in after the most recent 10 minute mark?
            val now10min =
                roundInstantToResolution(Clock.System.now(), 10.minutes) + 10.minutes

            val timeSortedHeartRateData =
                heartRateDao.getInRange(now10min - MLModel::INPUT_DAYS.get().days, now10min)
                    .sortedBy { it.time }

            if (timeSortedHeartRateData.isEmpty()) {
                delay(REFRESH_INTERVAL_MS)
                continue
            }

            val (inputHeartRate, inputHasDataMask) =
                prepareModelInput(timeSortedHeartRateData, now10min)

            val predictionOutput = mlModel.predict(
                inputHeartRate = inputHeartRate,
                inputHasDataMask = inputHasDataMask,
                now10min
            )

            updateDBWithPredictionOutput(
                inputHeartRate.takeLast(36).toFloatArray(),
                predictionOutput,
                now10min
            )

            delay(REFRESH_INTERVAL_MS)
        }
    }

    /**
     * Executes the worker's main logic, creates foreground notification and attempts restart
     * in case of exception in main loop
     */
    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        try {
            workerMainLoop()
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
                applicationContext.getString(R.string.energy_prediction_running_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.energy_prediction_running))
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
}
