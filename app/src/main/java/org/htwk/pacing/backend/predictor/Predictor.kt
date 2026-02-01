package org.htwk.pacing.backend.predictor

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.Predictor.predict
import org.htwk.pacing.backend.predictor.Predictor.train
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel.howFarInSamples
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.htwk.pacing.backend.predictor.preprocessing.ensureData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object Predictor {
    private const val LOGGING_TAG = "Predictor"

    //time duration/length of input time series;
    val TIME_SERIES_DURATION: Duration = 4.days
    val TIME_SERIES_STEP_DURATION: Duration = 30.minutes
    val TIME_SERIES_SAMPLE_COUNT: Int = (TIME_SERIES_DURATION / TIME_SERIES_STEP_DURATION).toInt()

    /**
     * A container for raw, unprocessed, synchronized data from the database, such as heart rate and distance.
     *
     * @property timeStart The common start time for all data streams.
     * @property duration The duration of the time series data.
     * @property heartRate A list of heart rate entries.
     * @property distance A list of distance entries.
     */
    data class MultiTimeSeriesEntries(
        val timeStart: Instant,
        val duration: Duration = TIME_SERIES_DURATION,

        val heartRate: List<HeartRateEntry>,
        val distance: List<DistanceEntry>,
        val elevationGained: List<ElevationGainedEntry>,
        val skinTemperature: List<SkinTemperatureEntry>,
        val heartRateVariability: List<HeartRateVariabilityEntry>,
        val oxygenSaturation: List<OxygenSaturationEntry>,
        val steps: List<StepsEntry>,
        val speed: List<SpeedEntry>,
        val sleepSession: List<SleepSessionEntry>,
    ) {
        companion object {
            //use in unit tests when you only care about certain metrics
            fun createDefaultEmpty(
                timeStart: Instant,
                duration: Duration = TIME_SERIES_DURATION,
                heartRate: List<HeartRateEntry> = listOf(),
                distance: List<DistanceEntry> = listOf(),
                elevationGained: List<ElevationGainedEntry> = listOf(),
                skinTemperature: List<SkinTemperatureEntry> = listOf(),
                heartRateVariability: List<HeartRateVariabilityEntry> = listOf(),
                oxygenSaturation: List<OxygenSaturationEntry> = listOf(),
                steps: List<StepsEntry> = listOf(),
                speed: List<SpeedEntry> = listOf(),
                sleepSession: List<SleepSessionEntry> = listOf(),
            ): MultiTimeSeriesEntries {
                return MultiTimeSeriesEntries(
                    timeStart = timeStart,
                    duration = duration,
                    heartRate = heartRate,
                    distance = distance,
                    elevationGained = elevationGained,
                    skinTemperature = skinTemperature,
                    heartRateVariability = heartRateVariability,
                    oxygenSaturation = oxygenSaturation,
                    steps = steps,
                    speed = speed,
                    sleepSession = sleepSession,
                )
            }
        }
    }

    /**
     * Encapsulates fixed parameters that do not change over the duration of a time series.
     * These are typically user-specific static values like physiological thresholds.
     *
     * @property anaerobicThresholdBPM The user's anaerobic threshold in beats per minute (BPM).
     *                                 This is the heart rate level above which the body's energy
     *                                 production becomes predominantly anaerobic.
     */
    data class FixedParameters(
        //we have to add more fixed vital parameters later
        val anaerobicThresholdBPM: Double
    )

    /**
     * Trains the prediction model on historical data.
     *
     * This function preprocesses the provided time series data and uses it to train the underlying
     * prediction model. It must be called with sufficient and representative sample data before
     * the `predict` function can be used to generate model predictions (forecasts).
     * Calling `train` updates the internal state of the model.
     *
     * @see predict The model must be trained before `predict` can be run.
     *
     * @param multiTimeSeriesDiscrete The historical multi-source time series data (e.g., heart rate, distance).
     * @param singleTargetTimeSeries The historical energy target values to train on
     */
    fun train(
        multiTimeSeriesDiscrete: MultiTimeSeriesDiscrete,
        singleTargetTimeSeries: TimeSeriesDiscretizer.SingleDiscreteTimeSeries,
    ) {
        DifferentialPredictionModel.train(multiTimeSeriesDiscrete, singleTargetTimeSeries.values)
    }

    /**
     * Runs the prediction model to forecast the energy level.
     *
     * This function uses recent time series data to predict the user's current and future
     * energy levels. It starts from the last known validated energy level and integrates
     * the model's predicted energy changes over the time since that validation.
     *
     * The prediction relies on a model that must be trained beforehand.
     *
     * @see train The model must be trained before `predict` can be run.
     *
     * @param multiTimeSeriesDiscrete The multi-source time series data (e.g., heart rate, steps)
     *                                up to the current time.
     * @param lastValidatedEnergy The last user-confirmed energy level (as a value between 0.0 and 1.0).
     * @param lastValidatedTime The timestamp of the last validated energy level.
     * @param timeNow The current time, serving as the reference point for the prediction.
     * @return A [PredictedEnergyLevelEntry] containing the forecasted energy level percentage for
     *         "now" and for a point in the "future", along with their respective timestamps.
     */
    fun predict(
        multiTimeSeriesDiscrete: MultiTimeSeriesDiscrete,
        lastValidatedEnergy: Double,
        lastValidatedTime: Instant,
        timeNow: Instant
    ): PredictedEnergyLevelEntry {
        //TODO: what if last validation is longer ago than TIME_SERIES_DURATION?
        //TODO: grab input data from a bit longer than 2 days

        val timeSinceLastValidation = timeNow - lastValidatedTime
        val stepsSinceLastValidation = (timeSinceLastValidation / TIME_SERIES_STEP_DURATION).toInt()

        if (stepsSinceLastValidation <= 0) return PredictedEnergyLevelEntry(
            time = timeNow,
            percentageNow = Percentage(lastValidatedEnergy.coerceIn(0.0, 1.0)),
            timeFuture = timeNow + IPredictionModel.PredictionHorizon.FUTURE.howFar,
            percentageFuture = Percentage(lastValidatedEnergy.coerceIn(0.0, 1.0))
        )

        val startIndex =
            (multiTimeSeriesDiscrete.stepCount() - stepsSinceLastValidation).coerceAtLeast(0)

        Log.i(LOGGING_TAG, "lastValidatedEnergy: $lastValidatedEnergy")
        Log.i(LOGGING_TAG, "lastValidatedTime: $lastValidatedTime")
        Log.i(LOGGING_TAG, "timeNow: $timeNow")
        Log.i(LOGGING_TAG, "stepsSinceLastValidation: $stepsSinceLastValidation")
        Log.i(LOGGING_TAG, "startIndex: $startIndex")

        val predictedEnergyNow = lastValidatedEnergy +
                accumulatePredictionsForHorizon(
                    startIndex, multiTimeSeriesDiscrete,
                    IPredictionModel.PredictionHorizon.NOW
                )

        Log.i(LOGGING_TAG, "predictedEnergyNow: $predictedEnergyNow")

        val predictedEnergyFuture = lastValidatedEnergy +
                accumulatePredictionsForHorizon(
                    startIndex, multiTimeSeriesDiscrete,
                    IPredictionModel.PredictionHorizon.FUTURE
                )

        Log.i(LOGGING_TAG, "predictedEnergyFuture: $predictedEnergyFuture")

        return PredictedEnergyLevelEntry(
            time = timeNow + IPredictionModel.PredictionHorizon.NOW.howFar,
            percentageNow = Percentage(predictedEnergyNow.coerceIn(0.0, 1.0)),
            timeFuture = timeNow + IPredictionModel.PredictionHorizon.FUTURE.howFar,
            percentageFuture = Percentage(predictedEnergyFuture.coerceIn(0.0, 1.0)),
        )
    }

    /**
     * Accumulates the predicted changes in energy over a specified time horizon.
     *
     * Iterates through a segment of the time series, starting from a given index,
     * and generates a series of predictions for the change in energy (deltas) at each step.
     * It then integrates these deltas to calculate the total accumulated energy change
     * from the start index up to the prediction horizon.
     *
     * @param startIndex The starting index in the time series from which to begin accumulating predictions.
     *                   This corresponds to the timestamp of the last validated energy level.
     * @param multiTimeSeriesDiscrete The discretized multi-source time series data used for prediction.
     * @param predictionHorizon An enum specifying the target time horizon for the prediction (e.g., NOW or FUTURE).
     *                          This determines the look-ahead window for the underlying prediction model.
     * @return A [Double] representing the total predicted change in energy level over the specified window.
     *         This value is the sum (integral) of all predicted energy deltas.
     */
    private fun accumulatePredictionsForHorizon(
        startIndex: Int,
        multiTimeSeriesDiscrete: MultiTimeSeriesDiscrete,
        predictionHorizon: IPredictionModel.PredictionHorizon
    ): Double {
        val rawEnergyDeltas =
            (startIndex - predictionHorizon.howFarInSamples until multiTimeSeriesDiscrete.stepCount()).map { i ->
                DifferentialPredictionModel.predict(
                    multiTimeSeriesDiscrete,
                    i,
                    predictionHorizon
                )
            }.toDoubleArray()

        //for smoothing, apply .causalExponentialMovingAverage(alpha = 1.0) to this
        val smoothedEnergyDeltas = rawEnergyDeltas

        //integrate delta steps to get absolute change of predicted energy in window
        val predictedEnergyFuture =
            smoothedEnergyDeltas.discreteTrapezoidalIntegral(0.0).last()
        return predictedEnergyFuture
    }
}

/**
 * Creates a discrete time series for the target variable (validated energy level).
 *
 * Uses ensureData to generate random filler values if nothing was entered yet.
 * This function is abstracted because its behaviour is required at different points in the code
 * and target data preprocessing should be unified.
 *
 * @param timeStart The start time of the desired time series.
 * @param duration The total duration of the time series.
 * @param validatedEnergyLevelEntries A list of user-validated energy level entries, which may be sparse.
 * @param stepCount The number of discrete steps (samples) the final time series should have.
 * @return A [TimeSeriesDiscretizer.SingleDiscreteTimeSeries] representing the uniformly sampled energy levels.
 */
fun generateDiscreteTargetSeries(
    timeStart: Instant,
    duration: Duration,
    validatedEnergyLevelEntries: List<ValidatedEnergyLevelEntry>,
    stepCount: Int
): TimeSeriesDiscretizer.SingleDiscreteTimeSeries =
    TimeSeriesDiscretizer.discretizeTimeSeries(
        ensureData(
            id = 1500,
            GenericTimedDataPointTimeSeries(
                timeStart = timeStart,
                duration = duration,
                isContinuous = true, //discretize: interpolate and edge fill validated energy level
                data = validatedEnergyLevelEntries.map { it ->
                    GenericTimedDataPoint(it.time, it.percentage.toDouble())
                }
            )
        ),
        targetLength = stepCount,
        //linear interpolation is ok, since we want linear behaviour on target metric
        interpolationMode = TimeSeriesDiscretizer.InterpolationMode.LINEAR
    )