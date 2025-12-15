package org.htwk.pacing.backend

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.EnergyPredictionJob.predictContinuous
import org.htwk.pacing.backend.EnergyPredictionJob.predictEvery
import org.htwk.pacing.backend.EnergyPredictionJob.retrainEvery
import org.htwk.pacing.backend.EnergyPredictionJob.trainOnce
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.Predictor.predict
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

    private val predictionSeriesDuration = Predictor.TIME_SERIES_DURATION
    private val maximumTrainingSeriesDuration = 30.days
    private val minimumTrainingSeriesDuration = 3.days
    private val retrainEvery = 1.hours
    private val predictEvery = 10.minutes

    /**
     * Entry point for the [EnergyPredictionJob].
     *
     * - Performs an initial training with [trainOnce].
     * - Launches a continuous prediction loop with [predictContinuous].
     * - Retrains the model periodically every [retrainEvery].
     *
     * @param db The [PacingDatabase] used to fetch heart rate, distance, user profile data,
     *           and store predicted energy levels.
     * @throws Exception Any exceptions thrown during prediction or training are propagated to the caller.
     */
    suspend fun run(db: PacingDatabase) = coroutineScope {
        trainOnce(db)
        launch { predictContinuous(db) }

        while (true) {
            delay(retrainEvery)
            trainOnce(db)
        }
    }

    /**
     * Continuously predicts energy levels based on live data.
     *
     * - Fetches live heart rate, distance, and user profile flows from the database.
     * - Updates every [predictEvery] duration or when the data changes.
     * - Generates a [MultiTimeSeriesEntries] and [FixedParameters] for each tick.
     * - Computes predictions via [predict] and inserts them into the database.
     *
     * @param db The [PacingDatabase] to fetch live data and store predictions.
     * @throws Exception All exceptions are propagated to the caller.
     */
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
        val userProfile = db.userProfileDao().getProfileLive()
        val ticker = flow {
            while (true) {
                delay(predictEvery)
                emit(Unit)
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
            userProfile,
            ticker,
        ) {
            val now = Clock.System.now()
            Pair(
                MultiTimeSeriesEntries(
                    timeStart = now - duration,
                    duration = duration,
                    heartRate = db.heartRateDao().getInRange(now - duration, now),
                    distance = db.distanceDao().getInRange(now - duration, now),
                    elevationGained = db.elevationGainedDao().getInRange(now - duration, now),
                    skinTemperature = db.skinTemperatureDao().getInRange(now - duration, now),
                    heartRateVariability = db.heartRateVariabilityDao().getInRange(now - duration, now),
                    oxygenSaturation = db.oxygenSaturationDao().getInRange(now - duration, now),
                    steps = db.stepsDao().getInRange(now - duration, now),
                    speed = db.speedDao().getInRange(now - duration, now),
                    sleepSession = db.sleepSessionsDao().getInRange(now - duration, now),
                ),
                FixedParameters(
                    anaerobicThresholdBPM = db.userProfileDao().getProfile()
                        ?.anaerobicThreshold?.toDouble()
                        ?: 0.0
                )
            )
        }.collect { (multiSeries, fixedParams) ->
            val energyPrediction = predict(multiSeries, fixedParams)
            Log.d(TAG, "Predicted: $energyPrediction")
            db.predictedEnergyLevelDao().insert(energyPrediction)
        }
    }

    /**
     * Trains the energy predictor model once using historical data.
     *
     * @param db Used to fetch historical data like heart rate, distance, and the user profile.
     * @throws Exception All exceptions are propagated to the caller.
     */
    private suspend fun trainOnce(db: PacingDatabase) {
        val latestEnd = Clock.System.now()
        val oldestStart = latestEnd - maximumTrainingSeriesDuration

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

        val allLists: List<List<TimedEntry>> = listOf(
            heartRate, distance, elevationGained, skinTemperature, heartRateVariability,
            oxygenSaturation, steps, speed, sleepSession, validatedEnergyLevel
        )

        var earliestEntryTime = allLists
            .mapNotNull { it.minByOrNull { entry -> entry.end }?.end }
            .minOrNull() ?: oldestStart

        val latestEntryTime = allLists
            .mapNotNull { it.maxByOrNull { entry -> entry.end }?.end }
            .maxOrNull() ?: latestEnd

        if (latestEntryTime - earliestEntryTime < minimumTrainingSeriesDuration) {
            earliestEntryTime = latestEntryTime - minimumTrainingSeriesDuration
        }
        val userProfile = db.userProfileDao().getProfile()

        val multiSeries = MultiTimeSeriesEntries(
            timeStart = earliestEntryTime,
            duration = latestEntryTime - earliestEntryTime,
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
        val fixedParams = FixedParameters(
            anaerobicThresholdBPM = userProfile
                ?.anaerobicThreshold?.toDouble()
                ?: 0.0
        )

        Predictor.train(multiSeries, targetEnergyTimeSeriesEntries = validatedEnergyLevel, fixedParams)
    }
}