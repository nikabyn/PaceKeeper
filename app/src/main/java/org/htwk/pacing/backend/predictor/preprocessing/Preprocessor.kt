package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.GenericTimeSeriesEntries
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer.discretizeTimeSeries

object Preprocessor : IPreprocessor {
    //class 3) (unused for now), see ui#38
    private fun processDailyConstant(): Double {
        return 0.0
    }

    /**
     * Executes the preprocessing pipeline on raw time series data.
     *
     * This function first cleans the input data and then transforms the raw, continuous time series
     * data for various metrics (like heart rate and distance) into a discrete, uniformly sampled
     * format suitable for the prediction model. It converts each metric into a common
     * [IPreprocessor.GenericTimeSeriesEntries] format before passing it to the [TimeSeriesDiscretizer]
     * for processing.
     *
     * @param raw The raw time series data, containing lists of data points for different metrics.
     * @param fixedParameters Additional fixed parameters that might influence the preprocessing. (Currently unused).
     * @return A [MultiTimeSeriesDiscrete] object containing the processed and discretized time series data.
     */
    override fun run(
        raw: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters
    ): MultiTimeSeriesDiscrete {
        val (rawCleaned, qualityRatios) = cleanInputData(raw)

        return MultiTimeSeriesDiscrete(
            timeStart = rawCleaned.timeStart,
            duration = rawCleaned.duration,
            heartRate = DiscretePID.from(
                discretizeTimeSeries(
                    GenericTimeSeriesEntries(
                        timeStart = rawCleaned.timeStart,
                        data = rawCleaned.heartRate.map(::GenericTimedDataPoint),
                        type = TimeSeriesType.CONTINUOUS
                    )
                )
            ),
            distance = DiscreteIntegral.from(
                discretizeTimeSeries(
                    GenericTimeSeriesEntries(
                        timeStart = rawCleaned.timeStart,
                        data = raw.distance.map(::GenericTimedDataPoint),
                        type = TimeSeriesType.AGGREGATED
                    )
                )
            )
        )
    }
}