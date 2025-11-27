package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete.Companion.timeStart
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer.discretizeTimeSeries
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import kotlin.time.Duration

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
        val stepSize = Predictor.TIME_SERIES_STEP_DURATION
        val timeStart: Instant = Instant.fromEpochMilliseconds(0)
        val featureIndexMap: Map<FeatureID, Int> = TimeSeriesMetric.values()
            .map { metric ->
                metric.signalClass.components.map { component ->
                    FeatureID(
                        metric,
                        component
                    )
                }
            }.flatten().mapIndexed { index, featureID -> featureID to index }.toMap()
    }

    private var length: Int = 0
    private var capacity: Int = initialCapacity
    private var featureMatrix: D2Array<Double> = mk.zeros(featureCount, capacity)

    fun getFeatureTimeSeries(featureID: FeatureID): D1Array<Double> {
        val index = featureIndexMap[featureID]!!;
        return featureMatrix[index] as D1Array<Double>;
    }

    fun getSampleOfFeature(featureID: FeatureID, index: Int): Double {
        require(index in 0..<length) { "Sample index $index out of bounds 0..<$length" }

        val row = featureIndexMap[featureID]!!;

        return featureMatrix[row, index]
    }

    //TODO: can this be made cheaper? (see user story, deep copy)
    fun getSampleOfAllFeatures(index: Int): D1Array<Double> {
        require(index in 0..<length) { "Sample index $index out of bounds 0..<$length" }

        // copy column at `index` into a new array, gives a sample of all features at one timestep
        val sample = DoubleArray(featureCount) { row ->
            featureMatrix[row, index]
        }

        return mk.ndarray(sample)
    }

    fun getDuration(): Duration {
        return stepSize * length
    }

    fun getSampleInstant(index: Int): Instant {
        require(index in 0..<length) { "Sample index $index out of bounds 0..<$length" }

        return timeStart + stepSize * index
    }

    fun getFeatureCount(): Int {
        return featureCount
    }

    fun length(): Int {
        return length
    }

    fun getAllFeatureIDs(): Set<FeatureID> {
        return featureIndexMap.keys
    }

    fun getFeatureView(featureID: FeatureID): NDArray<Double, D1> {
        val row = featureIndexMap[featureID]
            ?: throw IllegalArgumentException("Unknown feature: $featureID")

        //no copying - multi gives a mutable view into the matrix row here
        return featureMatrix[row] as NDArray<Double, D1>
    }

    fun growCapacity(newMinimumCapacity: Int) {
        if (newMinimumCapacity <= capacity) return;

        val newCapacity = maxOf(capacity * 2, newMinimumCapacity)
        val updated = mk.zeros<Double>(featureCount, newCapacity)

        // Copy existing data
        for (row in 0 until featureCount) {
            for (col in 0 until length) {
                updated[row, col] = featureMatrix[row, col]
            }
        }

        featureMatrix = updated
        capacity = newCapacity
    }

    /**
     * Append new steps to the time series efficiently through capacity reservation
     * `newSamples` shape must be [featureCount × newSteps]
     */
    private fun appendNewSamples(newSamples: D2Array<Double>) {
        val newSteps = newSamples.shape[1]
        if (newSteps == 0) return

        require(newSamples.shape[0] == featureCount) {
            "Expected ${featureCount} features, got ${newSamples.shape[0]}"
        }

        growCapacity(length + newSteps);

        // Copy new samples
        for (row in 0 until featureCount) {
            for (col in 0 until newSteps) {
                featureMatrix[row, length + col] = newSamples[row, col]
            }
        }

        length += newSteps
    }
}

fun MultiTimeSeriesDiscrete.Companion.fromEntries(raw: Predictor.MultiTimeSeriesEntries): MultiTimeSeriesDiscrete {
    val mtsd = MultiTimeSeriesDiscrete()

    featureIndexMap.keys.forEach { featureID ->
        val metric = featureID.metric

        //TODO: save another copy by passing a reference to the internal matrix to discretizeTimeSeries
        val discreteProportional = discretizeTimeSeries(
            IPreprocessor.GenericTimedDataPointTimeSeries(
                timeStart = raw.timeStart,
                duration = raw.duration,
                metric = metric,
                data = when (metric) {
                    TimeSeriesMetric.HEART_RATE -> raw.heartRate.map(::GenericTimedDataPoint)
                    TimeSeriesMetric.DISTANCE -> raw.distance.map(::GenericTimedDataPoint)
                }
            )
        )

        require(discreteProportional.isNotEmpty());

        mtsd.growCapacity(discreteProportional.size);

        // Generate other PID components where necessary.
        metric.signalClass.components.forEach { component ->
            val componentData = component.compute(discreteProportional)
            val featureView = mtsd.getFeatureView(featureID)

            // 3. Write the generated component series to the feature matrix.
            componentData.forEachIndexed { index, value -> featureView[index] = value }
        }
    }

    return mtsd
}
