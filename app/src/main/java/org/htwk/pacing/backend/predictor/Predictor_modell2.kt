package org.htwk.pacing.backend.predictor

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntryModell2
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.model.HeartRatePredictionModel_modell2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

object Predictor_modell2 {

    // 2 hours history as base for prediction
    val TIME_SERIES_DURATION: Duration = 2.hours

    /**
     * Input data for prediction including HR and validated energy for anchoring.
     */
    data class PredictionInput(
        val timeStart: Instant,
        val duration: Duration,
        val heartRate: List<HeartRateEntry>,
        val validatedEnergy: List<ValidatedEnergyLevelEntry>
    )

    fun train(
        heartRate: List<HeartRateEntry>,
        energy: List<ValidatedEnergyLevelEntry>
    ) {
        HeartRatePredictionModel_modell2.train(heartRate, energy)
    }

    /**
     * Predict using full input with anchoring to validated energy.
     * Returns Model 2 specific entry type.
     */
    fun predict(
        input: PredictionInput
    ): PredictedEnergyLevelEntryModell2 {
        val (now, future) = HeartRatePredictionModel_modell2.predict(
            recentHeartRate = input.heartRate,
            validatedEnergy = input.validatedEnergy
        )

        // 2h prediction horizon
        val futureHorizon = 2.hours

        return PredictedEnergyLevelEntryModell2(
            time = input.timeStart + input.duration,
            percentageNow = Percentage(now),
            timeFuture = input.timeStart + input.duration + futureHorizon,
            percentageFuture = Percentage(future)
        )
    }
}
