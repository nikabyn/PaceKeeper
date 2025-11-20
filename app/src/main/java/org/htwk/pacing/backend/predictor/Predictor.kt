package org.htwk.pacing.backend.predictor

import android.util.Log
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.model.LinearCombinationPredictionModel
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours


object Predictor {
    private const val TAG = "Predictor"

    //time duration/length of input time series
    val TIME_SERIES_DURATION: Duration = Duration.parse("2d")
    val TIME_SERIES_STEP_DURATION: Duration = Duration.parse("10m");
    val TIME_SERIES_SAMPLE_COUNT: Int = (TIME_SERIES_DURATION / TIME_SERIES_STEP_DURATION).toInt()
    val PREDICTION_WINDOW_DURATION: Duration = 2.hours
    val PREDICTION_WINDOW_SAMPLE_COUNT: Int =
        (PREDICTION_WINDOW_DURATION / TIME_SERIES_STEP_DURATION).toInt()

    /**
     * A container for raw, unprocessed, synchronized data from the database, such as heart rate and distance.
     *
     * @property timeStart The common start time for all data streams.
     * @property duration The duration of the time series data.
     * @property heartRate A list of heart rate entries.
     * @property distance A list of distance entries.
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
        var timeSeriesDiscrete: IPreprocessor.MultiTimeSeriesDiscrete
        val preprocessorDuration = measureTimeMillis {
            timeSeriesDiscrete = Preprocessor.run(inputTimeSeries, fixedParameters)
        }
        Log.d(TAG, "Preprocessor.run duration: $preprocessorDuration ms")

        val addSamplesDuration = measureTimeMillis {
            LinearCombinationPredictionModel.addTrainingSamplesFromMultiTimeSeriesDiscrete(
                timeSeriesDiscrete
            )
        }
        Log.d(TAG, "addTrainingSamplesFromMultiTimeSeriesDiscrete duration: $addSamplesDuration ms")

        val trainDuration =
            measureTimeMillis { LinearCombinationPredictionModel.trainOnStoredSamples() }
        Log.d(TAG, "trainOnStoredSamples duration: $trainDuration ms")
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

        //TODO: return actual energy prediction and a heartRate disguised as PredictedEnergyLevelEntry
        // 2.) run model and return energy prediction
        val predictedEnergy = LinearCombinationPredictionModel.predict(multiTimeSeriesDiscrete);
        return PredictedEnergyLevelEntry(
            inputTimeSeries.timeStart + TIME_SERIES_DURATION + PREDICTION_WINDOW_DURATION,
            Percentage(predictedEnergy / 100.0)
        );
    }
}