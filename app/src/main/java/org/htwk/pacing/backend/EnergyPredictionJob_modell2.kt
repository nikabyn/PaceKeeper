package org.htwk.pacing.backend

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.predictor.Predictor_modell2
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Alternative Job for Model 2.
 * Predicts based on Heart Rate with HRV modulation and anchoring to validated energy.
 *
 * Best configuration:
 * - Bereich=all, Methode=median, AutoFit=true, Offset=true, HRV=true, Anchor=true
 */
object EnergyPredictionJob_modell2 {
    const val TAG = "EnergyPredictionJob_modell2"

    private val predictionSeriesDuration = Predictor_modell2.TIME_SERIES_DURATION
    private val trainingDuration = 7.days // Train on last week
    private val retrainEvery = 2.hours
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
        val heartRate = db.heartRateDao().getChangeTrigger()
        val validatedEnergy = db.validatedEnergyLevelDao().getChangeTrigger()

        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(predictEvery)
            }
        }

        combine(
            heartRate,
            validatedEnergy,
            ticker,
            GlobalTime.offsetFlow, // Trigger when simulation time changes
        ) { _, _, _, _ ->
            val now = GlobalTime.now()
            // Fetch both HR and validated energy for the prediction window
            // Use a longer window for validated energy to ensure we have anchor points
            Predictor_modell2.PredictionInput(
                timeStart = now - duration,
                duration = duration,
                heartRate = db.heartRateDao().getInRange(now - duration, now),
                validatedEnergy = db.validatedEnergyLevelDao().getInRange(now - 1.days, now)
            )
        }.collect { input ->
            val energyPrediction = Predictor_modell2.predict(input)
            Log.d(TAG, "Predicted (Model 2): now=${energyPrediction.percentageNow}, " +
                    "future=${energyPrediction.percentageFuture}, " +
                    "hrPoints=${input.heartRate.size}, anchors=${input.validatedEnergy.size}")
            db.predictedEnergyLevelModell2Dao().insert(energyPrediction)
        }
    }

    private suspend fun trainOnce(db: PacingDatabase) {
        val latestEnd = Clock.System.now()
        val oldestStart = latestEnd - trainingDuration

        val heartRate = db.heartRateDao().getInRange(oldestStart, latestEnd)
        val validatedEnergy = db.validatedEnergyLevelDao().getInRange(oldestStart, latestEnd)

        Log.d(TAG, "Training with HR=${heartRate.size}, Energy=${validatedEnergy.size}")

        Predictor_modell2.train(
            heartRate = heartRate,
            energy = validatedEnergy
        )
        Log.i(TAG, "Model 2 trained")
    }
}
