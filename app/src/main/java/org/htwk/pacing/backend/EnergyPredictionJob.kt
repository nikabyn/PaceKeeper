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
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.Predictor.predict
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    private val trainingSeriesDuration = 30.days
    private val retrainEvery = 8.hours
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
        val heartRate = db.heartRateDao().getLastLive(duration)
        val distance = db.distanceDao().getLastLive(duration)
        val userProfile = db.userProfileDao().getProfileLive()
        val ticker = flow {
            delay(predictEvery)
            emit(Unit)
        }

        combine(
            heartRate,
            distance,
            userProfile,
            ticker
        ) { heartRate, distance, userProfile, _ ->
            Pair(
                MultiTimeSeriesEntries(
                    timeStart = Clock.System.now() - duration,
                    duration = duration,
                    heartRate = heartRate,
                    distance = distance,
                ),
                FixedParameters(
                    anaerobicThresholdBPM = userProfile
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
        val duration = trainingSeriesDuration
        val end = Clock.System.now()
        val start = end - duration

        val heartRate = db.heartRateDao().getInRange(start, end)
        val distance = db.distanceDao().getInRange(start, end)
        val userProfile = db.userProfileDao().getProfile()

        val multiSeries = MultiTimeSeriesEntries(
            timeStart = start,
            duration = duration,
            heartRate = heartRate,
            distance = distance,
        )
        val fixedParams = FixedParameters(
            anaerobicThresholdBPM = userProfile
                ?.anaerobicThreshold?.toDouble()
                ?: 0.0
        )

        Predictor.train(multiSeries, fixedParams)
    }
}