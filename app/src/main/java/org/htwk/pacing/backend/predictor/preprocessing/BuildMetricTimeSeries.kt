package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import kotlin.math.pow

/**
 * Builds a normalized time series for a given [TimeSeriesMetric] from raw predictor inputs.
 *
 * This function:
 * - Selects the appropriate raw signal based on [metric]
 * - Applies metric-specific preprocessing and physiological transformations
 *   (e.g. cardiac load amplification for heart rate, stress damping for HRV)
 * - Wraps the resulting data into a [GenericTimedDataPointTimeSeries] with
 *   correct timing and continuity metadata
 *
 * The returned time series is suitable for downstream processing such as
 * discretization, feature extraction, and model input preparation.
 *
 * @param metric The metric to construct the time series for
 * @param raw Container holding all raw, timestamped input signals
 * @param fixedParameters Fixed physiological parameters used for normalization
 *                        and stress-related adjustments
 *
 * @return A metric-specific [GenericTimedDataPointTimeSeries] containing
 *         preprocessed and time-aligned data points
 */

fun buildMetricTimeSeries(
    metric: TimeSeriesMetric,
    raw: Predictor.MultiTimeSeriesEntries,
    fixedParameters: Predictor.FixedParameters
): GenericTimedDataPointTimeSeries {

    val data = when (metric) {

        TimeSeriesMetric.HEART_RATE ->
            raw.heartRate.map { point ->
                val hr = point.bpm
                val threshold = fixedParameters.anaerobicThresholdBPM
                val overload = ((hr - threshold) / threshold).coerceAtLeast(0.0)

                val intensityFactor =
                    (1.0 + 5 * overload.pow(1.5)).coerceAtMost(1.6)

                val cardiacLoad = hr * intensityFactor
                GenericTimedDataPoint(point.time, cardiacLoad)
            }

        TimeSeriesMetric.DISTANCE ->
            raw.distance.map(::GenericTimedDataPoint)

        TimeSeriesMetric.ELEVATION_GAINED ->
            raw.elevationGained.map(::GenericTimedDataPoint)

        TimeSeriesMetric.SKIN_TEMPERATURE ->
            raw.skinTemperature.map(::GenericTimedDataPoint)

        TimeSeriesMetric.HEART_RATE_VARIABILITY ->
            raw.heartRateVariability.map { point ->
                val hrAtTime = raw.heartRate
                    .lastOrNull { it.time <= point.time }
                    ?.bpm

                val overload = if (hrAtTime != null) {
                    (hrAtTime - fixedParameters.anaerobicThresholdBPM).coerceAtLeast( 0.0 )
                } else {
                    0.0
                }

                val factor = (1.0 - overload / 100.0)
                    .coerceIn(0.5, 1.0)

                GenericTimedDataPoint(
                    point.time,
                    point.variability * factor
                )
            }

        TimeSeriesMetric.OXYGEN_SATURATION ->
            raw.oxygenSaturation.map(::GenericTimedDataPoint)

        TimeSeriesMetric.STEPS ->
            raw.steps.map(::GenericTimedDataPoint)

        TimeSeriesMetric.SPEED ->
            raw.speed.map(::GenericTimedDataPoint)

        TimeSeriesMetric.SLEEP_SESSION ->
            raw.sleepSession.map(::GenericTimedDataPoint)
    }

    return GenericTimedDataPointTimeSeries(
        timeStart = raw.timeStart,
        duration = raw.duration,
        isContinuous = metric.signalClass == TimeSeriesSignalClass.CONTINUOUS,
        data = data
    )
}