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


        /*val trainingSamples = (0 until 2).map { i ->
            val offset = (3.hours + 10.minutes) * i
            val sampleStartTime = inputTimeSeries.timeStart + offset
            val sampleEndTime = sampleStartTime + TIME_SERIES_DURATION

            val sampleTimeSeries = MultiTimeSeriesEntries(
                timeStart = sampleStartTime,
                heartRate = inputTimeSeries.heartRate.filter { it.time in sampleStartTime..sampleEndTime },
                distance = inputTimeSeries.distance.filter { it.end in sampleStartTime..sampleEndTime }
            )

            Preprocessor.run(sampleTimeSeries, fixedParameters)
        }*/

        val timeSeriesDiscrete = Preprocessor.run(inputTimeSeries, fixedParameters)




        /*trainingSamples.zipWithNext { currentSample, nextSample ->
            val predictionTarget = (nextSample.metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE]!! as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID).proportional[12]
            LinearCombinationPredictionModel.addTrainingSamplesFromMultiTimeSeriesDiscrete(
                currentSample,
                predictionTarget,
            )
        }*/


        val startTime = System.currentTimeMillis()

        LinearCombinationPredictionModel.addTrainingSamplesFromMultiTimeSeriesDiscrete(timeSeriesDiscrete)

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime // duration in nanoseconds
        print("Duration: $duration")

        val startTime1 = System.currentTimeMillis()

        LinearCombinationPredictionModel.trainOnStoredSamples()

        val endTime1 = System.currentTimeMillis()
        val duration1 = endTime1 - startTime1 // duration in nanoseconds
        print("Duration: $duration1")
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