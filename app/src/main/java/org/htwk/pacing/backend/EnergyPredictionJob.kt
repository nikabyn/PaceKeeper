package org.htwk.pacing.backend

import android.util.Log
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.EnergyPredictionJob.predictContinuous
import org.htwk.pacing.backend.EnergyPredictionJob.predictEvery
import org.htwk.pacing.backend.EnergyPredictionJob.retrainEvery
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.generateDiscreteTargetSeries
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds


/**
 * Responsible for continuously predicting the user's energy level using historical and live data.
 *
 * - Trains a predictor model periodically based on past data.
 * - Continuously predicts energy levels at regular intervals.
 * - Stores predictions using [PredictedEnergyLevelDao].
 *
 * This job is designed to run indefinitely. The training loop runs every [retrainEvery],
 * while predictions are generated every [predictEvery].
 */
object EnergyPredictionJob {
    const val TAG = "EnergyPredictionJob"

    private val predictionSeriesDuration = Predictor.TIME_SERIES_DURATION * 2
    private val maximumTrainingSeriesDuration = 60.days
    private val minimumTrainingSeriesDuration = predictionSeriesDuration * 2
    private val retrainEvery = 1.hours
    private val predictEvery = Predictor.TIME_SERIES_STEP_DURATION

    /**
     * Calculates a single energy level prediction at a specific point in time.
     *
     * This function serves as the core prediction logic, used by both the continuous
     * prediction job and the simulation mode. It gathers all necessary data within a
     * specific time window leading up to `timeNow`, preprocesses it, and then runs
     * the prediction model.
     *
     * The process involves:
     * 1. Finding the most recent validated energy level entry to use as an "anchor".
     * 2. Fetching all relevant time-series metrics (e.g., heart rate, steps, speed)
     *    from the database for the window `[timeNow - predictionSeriesDuration, timeNow]`.
     * 3. Retrieving fixed parameters, like the anaerobic threshold, from the user's profile.
     * 4. Running the preprocessor to discretize and normalize the time-series data.
     * 5. Executing the predictor with the processed data and the anchor energy level.
     *
     * @param db The [PacingDatabase] instance used to access all required DAOs for fetching data.
     * @param timeNow The specific [Instant] for which the energy level prediction is to be calculated.
     *                This represents the "current" time for the prediction.
     * @return A [PredictedEnergyLevelEntry] containing the calculated energy level, or `null` if
     *         prediction is not possible (e.g., no recent anchor energy level is found) or if an
     *         error occurs during the process.
     */
    suspend fun calculatePredictionAt(
        db: PacingDatabase,
        timeNow: Instant
    ): PredictedEnergyLevelEntry? {
        val duration = predictionSeriesDuration

        //fetch "anchor" energy level
        val anchorEntry = db.validatedEnergyLevelDao()
            .getInRange(timeNow - predictionSeriesDuration, timeNow)
            .maxByOrNull { it.time } ?: return null

        //fetch all metrics for the window [timeNow - duration, timeNow]
        val multiSeries = MultiTimeSeriesEntries(
            timeStart = timeNow - duration,
            duration = duration,
            heartRate = db.heartRateDao().getInRange(timeNow - duration, timeNow),
            distance = db.distanceDao().getInRange(timeNow - duration, timeNow),
            elevationGained = db.elevationGainedDao().getInRange(timeNow - duration, timeNow),
            skinTemperature = db.skinTemperatureDao().getInRange(timeNow - duration, timeNow),
            heartRateVariability = db.heartRateVariabilityDao()
                .getInRange(timeNow - duration, timeNow),
            oxygenSaturation = db.oxygenSaturationDao().getInRange(timeNow - duration, timeNow),
            steps = db.stepsDao().getInRange(timeNow - duration, timeNow),
            speed = db.speedDao().getInRange(timeNow - duration, timeNow),
            sleepSession = db.sleepSessionsDao().getInRange(timeNow - duration, timeNow),
        )

        //get fixed parameters from user profile
        val fixedParams = FixedParameters(
            anaerobicThresholdBPM = db.userProfileDao().getProfile()?.anaerobicThreshold?.toDouble()
                ?: 0.0
        )

        //run preprocessor and predictor
        return try {
            val multiTimeSeriesDiscrete = Preprocessor.run(multiSeries, fixedParams)
            Predictor.predict(
                multiTimeSeriesDiscrete = multiTimeSeriesDiscrete,
                lastValidatedEnergy = anchorEntry.percentage.toDouble(),
                lastValidatedTime = anchorEntry.time,
                timeNow = timeNow
            )
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed at $timeNow", e)
            null
        }
    }

    /**
     * Entry point for the [EnergyPredictionJob].
     *
     * - Performs an initial training with [tryTrainOnce].
     * - Launches a continuous prediction loop with [predictContinuous].
     * - Retrains the model periodically every [retrainEvery].
     *
     * @param db The [PacingDatabase] used to fetch heart rate, distance, user profile data,
     * and store predicted energy levels.
     * @throws Exception Any exceptions thrown during prediction or training are propagated to the caller.
     */
    suspend fun run(db: PacingDatabase) {
        db.userProfileDao().getProfileLive()
            .map { it?.simulationEnabled ?: false } // Extract the flag
            .distinctUntilChanged() // Only restart if the value actually changed
            .collectLatest { isSimulation ->

                Log.i(
                    TAG,
                    "Simulation Toggle set to : $isSimulation - restarting EnergyPredictionJob main logic"
                )

                // We open a coroutineScope here exactly like your original function.
                // When 'collectLatest' receives a new value, it automatically cancels this scope
                // and everything running inside it, then restarts the block.
                coroutineScope {
                    Log.i(TAG, "Starting Prediction Job. Simulation Mode: $isSimulation")

                    db.predictedEnergyLevelDao().deleteAll()

                    trainOnce(db)

                    if (isSimulation) {
                        launch { runSimulation(db) }
                    } else {
                        launch { predictContinuous(db) }

                        while (true) {
                            delay(retrainEvery)
                            trainOnce(db)
                        }
                    }
                }
            }
    }

    /**
     * Continuously predicts energy levels based on live data.
     *
     * - Fetches live heart rate, distance, and user profile flows from the database.
     * - Updates every [predictEvery] duration or when the data changes.
     * - Generates a [MultiTimeSeriesEntries] and [FixedParameters] for each tick.
     * - Computes predictions via [Predictor.predict] and inserts them into the database.
     *
     * @param db The [PacingDatabase] to fetch live data and store predictions.
     * @throws Exception All exceptions are propagated to the caller.
     */
    @OptIn(FlowPreview::class)
    private suspend fun predictContinuous(db: PacingDatabase) {
        val duration = predictionSeriesDuration
        val heartRate = db.heartRateDao().getChangeTrigger()
        val distance = db.distanceDao().getChangeTrigger()
        val elevationGained = db.elevationGainedDao().getChangeTrigger()
        val skinTemperature = db.skinTemperatureDao().getChangeTrigger()
        val heartRateVariability = db.heartRateVariabilityDao().getChangeTrigger()
        val oxygenSaturation = db.oxygenSaturationDao().getChangeTrigger()
        val steps = db.stepsDao().getChangeTrigger()
        val speed = db.speedDao().getChangeTrigger()
        val sleepSession = db.sleepSessionsDao().getChangeTrigger()
        val validatedEnergy = db.validatedEnergyLevelDao().getChangeTrigger() // Added Trigger

        val userProfile = db.userProfileDao().getProfileLive()
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(predictEvery)
            }
        }

        combine(
            heartRate,
            distance,
            elevationGained,
            skinTemperature,
            heartRateVariability,
            oxygenSaturation,
            steps,
            speed,
            sleepSession,
            validatedEnergy, // Include validation trigger
            userProfile,
            ticker,
        ) {
            val now = Clock.System.now()
            now
        }.debounce(1.seconds)
            .collect { simulatedNow ->
                val prediction = calculatePredictionAt(db, simulatedNow)
                if (prediction != null) {
                    Log.d(
                        TAG,
                        "Predicted Energy: ${prediction.percentageNow} at ${prediction.time} "
                    )
                    db.predictedEnergyLevelDao().insert(prediction)
                } else {
                    Log.e(
                        TAG,
                        "No entry to insert, since prediction failed"
                    )
                }
            }
    }

    /**
     * Runs a "timelapse" simulation of the energy prediction process using historical data.
     *
     * This function is intended for demonstration and debugging. It iterates through the time range
     * covered by existing validated energy level entries, generating predictions at discrete time steps.
     *
     * The simulation proceeds as follows:
     * - Clears any existing predicted energy levels to ensure a fresh simulation.
     * -  Fetches all historical `ValidatedEnergyLevelEntry`s to define the simulation's time boundaries.
     * -  Iterates from the earliest entry time, advancing in steps of `Predictor.TIME_SERIES_STEP_DURATION`.
     * -  At each step, it calls `calculatePredictionAt` to generate a new `PredictedEnergyLevelEntry`.
     * -  The new prediction is inserted into the database.
     * -  A small `delay` is introduced in each loop to allow the UI to animate the results, creating a
     *     "timelapse" effect.
     * -  The simulation stops once the current time step surpasses the time of the latest validated entry.
     *
     * @param db The [PacingDatabase] instance used to access historical health metrics, validated
     *           energy levels, and to store the generated predictions.
     */
    suspend fun runSimulation(db: PacingDatabase) {
        db.predictedEnergyLevelDao().deleteAll() // Clear old data for a fresh start

        val allValidations = db.validatedEnergyLevelDao().getAll()
        if (allValidations.isEmpty()) return

        val earliestEntryTime = allValidations.minBy { it.time }.time
        val latestEntryTime = allValidations.maxBy { it.time }.time

        var simCount = 0

        while (true) {
            //increment time in 30-minute steps, matching desired simulation speed
            val now = earliestEntryTime + Predictor.TIME_SERIES_STEP_DURATION * simCount + 2.days

            //stop the simulation if it reaches the last validated entry
            if (now > latestEntryTime) break

            val prediction = calculatePredictionAt(db, now)
            print("pred: $prediction")
            if (prediction != null) {
                db.predictedEnergyLevelDao().insert(prediction)
            }
            simCount++
            delay(1000) //small delay to allow the UI to animate the "timelapse"
        }
    }

    /**
     * Trains the energy predictor model once using historical data.
     *
     * This function fetches a range of historical health metrics and validated energy levels
     * from the database. The duration of this historical data is determined by the span
     * of validated energy entries, constrained between `minimumTrainingSeriesDuration`
     * and `maximumTrainingSeriesDuration`.
     *
     * The process involves:
     * - Determining the time window for training data based on available validated energy levels.
     * - Fetching various time-series data (e.g., heart rate, distance, sleep) and fixed user
     *    parameters (e.g., anaerobic threshold) from the database for that window.
     * - Preprocessing the raw data into a discrete, uniform format using the `Preprocessor`.
     * - Generating a corresponding discrete target series from the validated energy levels.
     * - Passing both the preprocessed input data and the target data to `Predictor.train`
     *    to update the model.
     *
     * @param db The [PacingDatabase] instance used to fetch all required historical data.
     * @throws Exception Propagates any exceptions that occur during data fetching or model training.
     */
    private suspend fun trainOnce(db: PacingDatabase) {
        val allValidatedEntries = db.validatedEnergyLevelDao().getAll()

        val now = Clock.System.now()

        //grab latest validation entry, or use time now
        val earliestValidatedTime =
            (allValidatedEntries.minByOrNull { it.time }?.time ?: now) - predictionSeriesDuration
        val latestValidatedTime = allValidatedEntries.maxByOrNull { it.time }?.time ?: now

        val validationsDuration =
            maxOf(
                latestValidatedTime - earliestValidatedTime,
                minimumTrainingSeriesDuration
            ).coerceAtMost(maximumTrainingSeriesDuration)

        val latestEnd = latestValidatedTime

        val oldestStart = latestEnd - validationsDuration

        val heartRate = db.heartRateDao().getInRange(oldestStart, latestEnd)
        val distance = db.distanceDao().getInRange(oldestStart, latestEnd)
        val elevationGained = db.elevationGainedDao().getInRange(oldestStart, latestEnd)
        val skinTemperature = db.skinTemperatureDao().getInRange(oldestStart, latestEnd)
        val heartRateVariability = db.heartRateVariabilityDao().getInRange(oldestStart, latestEnd)
        val oxygenSaturation = db.oxygenSaturationDao().getInRange(oldestStart, latestEnd)
        val steps = db.stepsDao().getInRange(oldestStart, latestEnd)
        val speed = db.speedDao().getInRange(oldestStart, latestEnd)
        val sleepSession = db.sleepSessionsDao().getInRange(oldestStart, latestEnd)
        val validatedEnergyLevel = db.validatedEnergyLevelDao().getInRange(oldestStart, latestEnd)

        val userProfile = db.userProfileDao().getProfile()

        val multiTimeSeriesEntries = MultiTimeSeriesEntries(
            timeStart = oldestStart,
            duration = validationsDuration,
            heartRate = heartRate,
            distance = distance,
            elevationGained = elevationGained,
            skinTemperature = skinTemperature,
            heartRateVariability = heartRateVariability,
            oxygenSaturation = oxygenSaturation,
            steps = steps,
            speed = speed,
            sleepSession = sleepSession,
        )
        val fixedParameters = FixedParameters(
            anaerobicThresholdBPM = userProfile
                ?.anaerobicThreshold?.toDouble()
                ?: 0.0
        )

        val multiTimeSeriesDiscrete = Preprocessor.run(multiTimeSeriesEntries, fixedParameters)
        val targetTimeSeries = generateDiscreteTargetSeries(
            multiTimeSeriesEntries.timeStart,
            multiTimeSeriesEntries.duration,
            validatedEnergyLevel,
            multiTimeSeriesDiscrete.stepCount()
        )

        Predictor.train(multiTimeSeriesDiscrete, targetTimeSeries)
    }
}