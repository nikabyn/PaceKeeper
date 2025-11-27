package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import kotlin.time.Duration

/**
 * The IPreprocessor interface defines the contract for preprocessing raw time series data.
 *
 * Preprocessors are responsible for taking raw, potentially noisy, and irregularly sampled time series data
 * (like heart rate and distance) and transforming it into a clean, uniformly sampled format suitable for
 * a prediction model. This process involves cleaning, resampling, and deriving features like integrals
 * and derivatives.
 */
interface IPreprocessor {
    /**
     * A generic container for a single time series before preprocessing.
     * @property timeStart The starting timestamp of the time series.
     * @property data The list of timed data points.
     * @property signalClass The type of the time series (continuous or aggregated).
     */
    data class GenericTimedDataPointTimeSeries(
        val timeStart: Instant,
        val duration: Duration,
        val metric: TimeSeriesMetric,
        val data: List<GenericTimedDataPoint>,
    )

    /**
     * Holds data quality metrics calculated during preprocessing.
     * @property cleanedHeartRatesRatio The percentage of heart rate data points remaining after cleaning.
     * @property cleanedDistancesRatio The percentage of distance data points remaining after cleaning.
     */
    data class QualityRatios(
        val cleanedHeartRatesRatio: Percentage,
        val cleanedDistancesRatio: Percentage
    )

    /**
     * Executes the preprocessing pipeline.
     *
     * @param raw The raw, multi-series input data from the predictor.
     * @param fixedParameters The fixed parameters for the prediction, used for resampling configuration.
     * @return A [MultiTimeSeriesDiscrete] object containing the cleaned, resampled, and feature-engineered data.
     */
    fun run(
        raw: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters
    ): MultiTimeSeriesDiscrete
}