package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

/**
 * Computes the decaying load of a time series. (EWMA Filter)
 *
 * @param alpha The decay factor.
 *
 */
fun DoubleArray.decayingLoad(alpha: Double = 0.1): DoubleArray {
    val out = DoubleArray(size)
    var load = 0.0
    for (i in indices) {
        load = load * (1 - alpha) + this[i]
        out[i] = load
    }
    return out
}

/**
 * Represents the derived feature component of a discrete time series.
 *
 * Each component defines a specific mathematical operation to be applied to a `DoubleArray`
 * representing a time series.
 *
 * @property compute A lambda function that takes a `DoubleArray` and calculates the respective
 * PID component as a new `DoubleArray`
 */
enum class FeatureComponent(val compute: (DoubleArray) -> DoubleArray) {
    PROPORTIONAL({ it }), //proportional: return the array itself, without changing it
    INTEGRAL(DoubleArray::discreteTrapezoidalIntegral), //compute integral of input
    DERIVATIVE(DoubleArray::discreteDerivative), //compute derivative of input

    EWMA(DoubleArray::decayingLoad), //compute decaying load of input

    //square input value, can be used to penalize large values, e.g. heavy heart rate load
    SQUARED({ arr -> arr.map { it * it }.toDoubleArray() })
}

/**
 * Differentiates time series signals by their physical nature to determine which
 * PID components are meaningful to compute.
 *
 * See [UI issue #38](https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963)
 * for a detailed explanation of signal "classes".
 *
 * @property components The [FeatureComponent]s applicable to this signal class.
 */
enum class TimeSeriesSignalClass(val components: List<FeatureComponent>) {
    /** For values that change continuously over time, like heart rate. */
    CONTINUOUS(listOf(
        FeatureComponent.PROPORTIONAL,
        FeatureComponent.DERIVATIVE,
        //PIDComponent.INTEGRAL,
        //we don't need integral, because extrapolation averages encode same summation behaviour
    )),

    /** For values that accumulate over time, like total steps or distance. */
    AGGREGATED(listOf(FeatureComponent.PROPORTIONAL/*, PIDComponent.INTEGRAL*/)),
}

/**
 * Defines specific metrics for time series data, such as heart rate or distance.
 *
 * Each metric is categorized by a [TimeSeriesSignalClass] to determine its applicable
 * processing components (e.g., [FeatureComponent]s).
 *
 * @property signalClass The signal classification for this metric, determines which PID components will be derived
 */
enum class TimeSeriesMetric(val signalClass: TimeSeriesSignalClass, val auxiliaryFeatures: List<FeatureComponent> = listOf()) {
    HEART_RATE(TimeSeriesSignalClass.CONTINUOUS,
        auxiliaryFeatures = listOf(FeatureComponent.EWMA, FeatureComponent.SQUARED)
    ),
    DISTANCE(TimeSeriesSignalClass.AGGREGATED),
    ELEVATION_GAINED(TimeSeriesSignalClass.AGGREGATED),
    HEART_RATE_VARIABILITY(TimeSeriesSignalClass.CONTINUOUS),
    OXYGEN_SATURATION(TimeSeriesSignalClass.CONTINUOUS),
    SKIN_TEMPERATURE(TimeSeriesSignalClass.CONTINUOUS),
    STEPS(TimeSeriesSignalClass.AGGREGATED),
    SPEED(TimeSeriesSignalClass.CONTINUOUS),
    SLEEP_SESSION(TimeSeriesSignalClass.AGGREGATED)
}

val TimeSeriesMetric.allComponents: List<FeatureComponent>
    get() = signalClass.components + auxiliaryFeatures
