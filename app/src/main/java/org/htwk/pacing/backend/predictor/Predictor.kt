package org.htwk.pacing.backend.predictor

import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.model.LinearCombinationPredictionModel
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


class Predictor {
    companion object {
        //time duration/length of input time series
        val TIME_SERIES_DURATION: Duration =
            2.days //TODO: see what actually makes sense as duration
        val TIME_SERIES_SAMPLE_COUNT: Int =
            TIME_SERIES_DURATION.inWholeHours.toInt() * 6 // 2 days of 10-min steps

        val PREDICTION_WINDOW_DURATION: Duration = 2.hours

        val TIME_SERIES_STEP_DURATION: Duration =
            10.minutes; //TODO: see what actually makes sense as duration
    }

    /**
     * A container for raw, unprocessed, synchronized data from database, like heart rate.
     *
     * @property timeStart The common start time for all data streams.
     * @property metrics A map of metric types to their corresponding time series data.
     */
    data class MultiTimeSeriesEntries(
        val timeStart: kotlinx.datetime.Instant,
        val duration: Duration = TIME_SERIES_DURATION,

        val heartRate: List<HeartRateEntry>,
        val distance: List<DistanceEntry>
    )

    /**
     * Encapsulates fixed parameters that do not change over the duration of a time series.
     * These are typically user-specific static values like age or physiological thresholds.
     */
    data class FixedParameters(
        //we have to add more fixed vital parameters later
        val anaerobicThresholdBPM: Double
    )

    fun train(
        inputTimeSeries: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters,
    ) {
        val stepSize = 3.hours + 33.minutes

        val trainingSampleCount = 100
            //(inputTimeSeries.duration / Predictor.TIME_SERIES_DURATION).toInt()

        val trainingSamples = (0 until trainingSampleCount).map { i ->
            //create a copy of inputTimeSeries that only contains the data for two days, offset by i * 2 days
            val offset = stepSize * i
            val sampleStartTime = inputTimeSeries.timeStart + offset
            val sampleEndTime = sampleStartTime + TIME_SERIES_DURATION

            val sampleHeartRate =
                inputTimeSeries.heartRate.filter { it.time in sampleStartTime..sampleEndTime }
            val sampleDistance =
                inputTimeSeries.distance.filter { it.end in sampleStartTime..sampleEndTime }

            val sampleTimeSeries = inputTimeSeries.copy(
                timeStart = sampleStartTime,
                heartRate = sampleHeartRate,
                distance = sampleDistance
            )

            Preprocessor.run(sampleTimeSeries, fixedParameters)
        }

        //iterate through training samples, leaving out the last one
        for (i in 0 until trainingSampleCount - 1) {
            val predictionTarget =
                (trainingSamples[i + 1].metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE]!! as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID).proportional[12]

            LinearCombinationPredictionModel.addTrainingSampleFromMultiTimeSeriesDiscrete(
                trainingSamples[i],
                predictionTarget
            )
        }


        LinearCombinationPredictionModel.trainOnStoredSamples()
    }

    /**
     * Runs the prediction model to forecast the energy level.
     *
     * This function takes historical time series data and fixed user parameters as input,
     * preprocesses the data, and then feeds it into a prediction model to generate
     * a future energy level prediction.
     *
     * @param inputTimeSeries The multi-source time series data (e.g., heart rate) for a defined duration.
     * @param fixedParameters Static user-specific parameters, such as the anaerobic threshold, that do not change over the time series.
     * @return A [PredictedEnergyLevelEntry] containing the forecasted energy level percentage and the timestamp for which the prediction is valid.
     */
    fun predict(
        inputTimeSeries: MultiTimeSeriesEntries, /*fixed parameters like anaerobic threshold*/
        fixedParameters: FixedParameters,
    ): PredictedEnergyLevelEntry {
        // 1.) time series preprocessing
        val multiTimeSeriesDiscrete = Preprocessor.run(inputTimeSeries, fixedParameters)
        // (1.5) TODO: cache (don't need that for now)

        // 2.) run model and return energy prediction
        val predictedEnergy = LinearCombinationPredictionModel.predict(multiTimeSeriesDiscrete);
        return PredictedEnergyLevelEntry(
            inputTimeSeries.timeStart + TIME_SERIES_DURATION + PREDICTION_WINDOW_DURATION,
            Percentage(predictedEnergy / 100.0)
        );
    }
}