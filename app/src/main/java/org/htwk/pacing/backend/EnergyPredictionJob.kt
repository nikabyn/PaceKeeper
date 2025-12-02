package org.htwk.pacing.backend

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.Predictor.predict
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object EnergyPredictionJob {
    const val TAG = "EnergyPredictionJob"

    private val predictionSeriesDuration = Predictor.TIME_SERIES_DURATION
    private val trainingSeriesDuration = 30.days
    private val retrainEvery = 8.hours
    private val predictEvery = 10.minutes


    suspend fun run(db: PacingDatabase) = coroutineScope {
        trainOnce(db)
        launch { predictContinuous(db) }

        while (true) {
            delay(retrainEvery)
            trainOnce(db)
        }
    }

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