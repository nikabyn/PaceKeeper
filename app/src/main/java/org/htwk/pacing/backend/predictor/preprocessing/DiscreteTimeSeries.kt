package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set


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

/**
 * A container for multiple, preprocessed, and discrete time series, ready for the model.
 * This is an internal data structure used between the preprocessor and the model.
 * @property timeStart The common starting timestamp for all contained time series.
 * @property metrics The preprocessed time series data, keyed by metric type.
 */
/*data class MultiTimeSeriesDiscrete(
    val timeStart: Instant,
    val duration: Duration,
    val metrics: Map<TimeSeriesMetric, List<DoubleArray>>
)*/

class MultiTimeSeriesDiscrete(initialCapacity: Int = 512) {
    data class FeatureID(
        val metric: TimeSeriesMetric,
        val component: PIDComponent
    )

    companion object {
        private val featureCount: Int =
            TimeSeriesMetric.entries.sumOf { it.signalClass.components.size }
    }

    private var stepCount: Int = 0
    private var capacity: Int = initialCapacity
    private val stepSize = Predictor.TIME_SERIES_STEP_DURATION
    private val timeStart: Instant = Instant.fromEpochMilliseconds(0)
    private var featureMatrix: D2Array<Double> = mk.zeros(featureCount, stepCount)

    private val featureIndexMap: Map<FeatureID, Int> = TimeSeriesMetric.values()
        .map { metric ->
            metric.signalClass.components.map { component ->
                FeatureID(
                    metric,
                    component
                )
            }
        }.flatten().mapIndexed { index, featureID -> featureID to index }.toMap()

    fun getFeatureTimeSeries(featureID: FeatureID): D1Array<Double> {
        val index = featureIndexMap[featureID]!!;
        return featureMatrix[index] as D1Array<Double>;
    }

    //TODO: can this be made cheaper? (see user story, deep copy)
    fun getSample(index: Int): D1Array<Double> {
        require(index in 0..<stepCount) { "Sample index $index out of bounds 0..<$stepCount" }

        // copy column at `index` into a new array, gives a sample of all features at one timestep
        val sample = DoubleArray(featureCount) { row ->
            featureMatrix[row, index]
        }

        return mk.ndarray(sample)
    }


    fun getTimeSeriesLength(): Int {
        return stepCount
    }

    /**
     * Append new steps to the time series efficiently through capacity reservation
     * `newSamples` shape must be [featureCount × newSteps]
     */
    private fun appendDiscrete(newSamples: D2Array<Double>) {
        val newSteps = newSamples.shape[1]
        if (newSteps == 0) return

        require(newSamples.shape[0] == featureCount) {
            "Expected ${featureCount} features, got ${newSamples.shape[0]}"
        }

        // Grow matrix if necessary
        if (stepCount + newSteps > capacity) {
            val newCapacity = maxOf(capacity * 2, stepCount + newSteps)
            val updated = mk.zeros<Double>(featureCount, newCapacity)

            // Copy existing data
            for (row in 0 until featureCount) {
                for (col in 0 until stepCount) {
                    updated[row, col] = featureMatrix[row, col]
                }
            }

            featureMatrix = updated
            capacity = newCapacity
        }

        // Copy new samples
        for (row in 0 until featureCount) {
            for (col in 0 until newSteps) {
                featureMatrix[row, stepCount + col] = newSamples[row, col]
            }
        }

        stepCount += newSteps
    }
}