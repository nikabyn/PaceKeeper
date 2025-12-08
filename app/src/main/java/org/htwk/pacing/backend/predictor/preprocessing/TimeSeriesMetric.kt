package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

/**
 * Represents the derived Proportional-Integral-Derivative of a discrete time series.
 *
 * Each component defines a specific mathematical operation to be applied to a `DoubleArray`
 * representing a time series.
 *
 * @property compute A lambda function that takes a `DoubleArray` and calculates the respective
 * PID component as a new `DoubleArray`
 */
enum class PIDComponent(val compute: (DoubleArray) -> DoubleArray) {
    PROPORTIONAL({ it }), //proportional: return the array itself, without changing it
    INTEGRAL(DoubleArray::discreteTrapezoidalIntegral), //compute integral of input
    DERIVATIVE(DoubleArray::discreteDerivative), //compute derivative of input
}

/**
 * Differentiates time series signals by their physical nature to determine which
 * PID components are meaningful to compute.
 *
 * See [UI issue #38](https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963)
 * for a detailed explanation of signal "classes".
 *
 * @property components The [PIDComponent]s applicable to this signal class.
 */
enum class TimeSeriesSignalClass(val components: List<PIDComponent>) {
    /** For values that change continuously over time, like heart rate. */
    CONTINUOUS(listOf(PIDComponent.PROPORTIONAL, PIDComponent.INTEGRAL, PIDComponent.DERIVATIVE)),

    /** For values that accumulate over time, like total steps or distance. */
    AGGREGATED(listOf(PIDComponent.INTEGRAL)),
}

/**
 * Defines specific metrics for time series data, such as heart rate or distance.
 *
 * Each metric is categorized by a [TimeSeriesSignalClass] to determine its applicable
 * processing components (e.g., [PIDComponent]s).
 *
 * @property signalClass The signal classification for this metric, determines which PID components will be derived
 */
enum class TimeSeriesMetric(val signalClass: TimeSeriesSignalClass) {
    HEART_RATE(TimeSeriesSignalClass.CONTINUOUS),
    DISTANCE(TimeSeriesSignalClass.AGGREGATED),
    ELEVATION_GAINED(TimeSeriesSignalClass.AGGREGATED),
    HEART_RATE_VARIABILITY(TimeSeriesSignalClass.CONTINUOUS),
    OXYGEN_SATURATION(TimeSeriesSignalClass.CONTINUOUS),
    SKIN_TEMPERATURE(TimeSeriesSignalClass.CONTINUOUS),
    STEPS(TimeSeriesSignalClass.AGGREGATED),
    Speed(TimeSeriesSignalClass.CONTINUOUS)

}