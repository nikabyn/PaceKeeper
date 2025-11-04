package org.htwk.pacing.backend.predictor

import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.model.Model
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours


class Predictor {
    companion object {
        //time duration/length of input time series
        val TIME_SERIES_DURATION: Duration = 1.days;
        val TIME_SERIES_SAMPLE_COUNT: Int =
            TIME_SERIES_DURATION.inWholeHours.toInt() * 6 // 2 days of 10-min steps

        val PREDICTION_WINDOW_DURATION: Duration = 2.hours
    }

    data class MultiTimeSeriesEntries(
        val timeStart: kotlinx.datetime.Instant,
        val heartRate: List<HeartRateEntry> //TODO: add more vitals
    )

    data class FixedParameters(
        //TODO: add more fixed vital parameters
        val anaerobicThreshold: Float
    )

    fun run(
        inputTimeSeries: MultiTimeSeriesEntries, /*fixed parameters like anaerobic threshold*/
        fixedParameters: FixedParameters,
    ): PredictedEnergyLevelEntry {
        // 1.) time series preprocessing
        val multiTimeSeriesDiscrete = Preprocessor.run(inputTimeSeries, fixedParameters)
        // (1.5) TODO: cache (don't need that for now)

        // 2.) run model and return energy prediction
        val predictedEnergy = Model.predict(multiTimeSeriesDiscrete);
        return PredictedEnergyLevelEntry(
            inputTimeSeries.timeStart + TIME_SERIES_DURATION,
            Percentage(predictedEnergy)
        );
    }
}