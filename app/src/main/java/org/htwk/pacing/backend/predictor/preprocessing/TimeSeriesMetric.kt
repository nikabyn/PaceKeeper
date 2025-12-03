package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

enum class PIDComponent(val compute: (DoubleArray) -> DoubleArray) {
    PROPORTIONAL({ it }), // The "P" component is the input itself.
    INTEGRAL(DoubleArray::discreteTrapezoidalIntegral),
    DERIVATIVE(DoubleArray::discreteDerivative),
}

/**
 * Enum to differentiate between time series types.
 * see ui#38 for explanation of "classes" https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963
 */
enum class TimeSeriesSignalClass(val components: List<PIDComponent>) {
    /** For values that change continuously over time, like heart rate. */
    CONTINUOUS(listOf(PIDComponent.PROPORTIONAL, PIDComponent.INTEGRAL, PIDComponent.DERIVATIVE)),

    /** For values that accumulate over time, like total steps or distance. */
    AGGREGATED(listOf(PIDComponent.INTEGRAL)),
}

enum class TimeSeriesMetric(val signalClass: TimeSeriesSignalClass) {
    HEART_RATE(TimeSeriesSignalClass.CONTINUOUS),
    DISTANCE(TimeSeriesSignalClass.AGGREGATED),
}