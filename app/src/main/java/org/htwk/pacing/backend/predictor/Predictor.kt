package org.htwk.pacing.backend.predictor

import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.model.Model
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor


class Predictor {
    data class MultiTimeSeriesList(
        val heartRate: List<HeartRateEntry>,
        val distance: List<DistanceEntry>
    )

    data class FixeParameters(
        val heartRate: List<HeartRateEntry>,
        val distance: List<DistanceEntry>
    )

    data class MultiTimeSeriesSamples(
        //TODO: clean up/change, how to represent multi-dimensional time series in kotlin
        val timeStart: kotlinx.datetime.Instant,
        val heartRate: FloatArray,
        val distance: FloatArray
    )

    fun run(
        inputTimeSeries: MultiTimeSeriesList, /*fixed parameters like anaerobic threshold*/
        fixedParameters: FixeParameters,
    ): List<PredictedEnergyLevelEntry> {

        // 1.) time series preprocessing
        val multiTimeSeriesSamples = Preprocessor.run(inputTimeSeries)
        // (1.5) TODO: cache (don't need that for now)

        // 2.) run model
        val predictedEnergySamples = Model.predict(multiTimeSeriesSamples)

        //return energy prediction
        val predictedEnergy: List<PredictedEnergyLevelEntry> = emptyList()
        return predictedEnergy
    }
}