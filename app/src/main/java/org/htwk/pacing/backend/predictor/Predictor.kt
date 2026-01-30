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
import org.htwk.pacing.backend.predictor.Predictor.train
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel.howFarInSamples
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.htwk.pacing.backend.predictor.preprocessing.ensureData
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral
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
     * the `predict` function can be used to generate accurate forecasts.
     * Calling `train` updates the internal state of the model.
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
     * This function takes historical time series data and fixed user parameters as input,
     * preprocesses the data, and then feeds it into a prediction model to generate
     * a future energy level prediction.
     *
     * @see train train must be called before predict/inference can run
     *
     * @param inputTimeSeries The multi-source time series data (e.g., heart rate) for a defined duration.
     * @param fixedParameters Static user-specific parameters, such as the anaerobic threshold, that do not change over the time series.
     * @return A [PredictedEnergyLevelEntry] containing the forecasted energy level percentage and the timestamp for which the prediction is valid.
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

        if(stepsSinceLastValidation == 0) return PredictedEnergyLevelEntry(
            time = timeNow,
            percentageNow = Percentage(lastValidatedEnergy.coerceIn(-1.0, 1.0)),
            timeFuture = multiTimeSeriesDiscrete.timeStart + TIME_SERIES_DURATION + IPredictionModel.PredictionHorizon.FUTURE.howFar,
            percentageFuture = Percentage(0.0 + Double.NaN)
        )


        var startIndex = multiTimeSeriesDiscrete.stepCount() - stepsSinceLastValidation

        //walk through time until now, predicting change in each step, to answer where energy is now
        val rawDeltasNow = (startIndex until multiTimeSeriesDiscrete.stepCount()).map { i ->
            DifferentialPredictionModel.predict(
                multiTimeSeriesDiscrete,
                i,
                IPredictionModel.PredictionHorizon.NOW
            )
        }.toDoubleArray()

        val predictedEnergyNow = rawDeltasNow.discreteTrapezoidalIntegral(lastValidatedEnergy).last()

        val rawDeltasFuture = (startIndex - IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples until multiTimeSeriesDiscrete.stepCount()).map { i ->
            DifferentialPredictionModel.predict(
                multiTimeSeriesDiscrete,
                i,
                IPredictionModel.PredictionHorizon.FUTURE
            )
        }.toDoubleArray()

        val predictedEnergyFuture = rawDeltasFuture.discreteTrapezoidalIntegral(lastValidatedEnergy).last()
        /*DifferentialPredictionModel.predict(
            multiTimeSeriesDiscrete,
            IPredictionModel.PredictionHorizon.NOW
        );*/
        /*val predictedEnergyFuture = ExtrapolationPredictionModel.predict(
            multiTimeSeriesDiscrete,
            IPredictionModel.PredictionHorizon.FUTURE
        );*/
        return PredictedEnergyLevelEntry(
            time = timeNow + IPredictionModel.PredictionHorizon.NOW.howFar,
            percentageNow = Percentage(predictedEnergyNow.coerceIn(0.0, 1.0)),
            timeFuture = timeNow + IPredictionModel.PredictionHorizon.FUTURE.howFar,
            percentageFuture = Percentage(predictedEnergyFuture.coerceIn(0.0, 1.0)),
        )
    }
}

fun generateDiscreteTargetSeries(timeStart: Instant, duration: Duration, validatedEnergyLevelEntries: List<ValidatedEnergyLevelEntry>, stepCount: Int): TimeSeriesDiscretizer.SingleDiscreteTimeSeries =
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