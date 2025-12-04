package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
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
 * A container for multiple, aligned, discrete time series, optimized for model input.
 *
 * All time series share the same starting timestamp (`timeStart`) and are sampled using a
 * fixed interval (`stepSize`). Each metric can have multiple feature components (e.g. P/I/D)
 * and each of these becomes one row in a shared 2D matrix. Columns correspond to discrete
 * time steps
 *
 *     Rows    = Features  (metric x PID component)
 *     Columns = Timesteps (from time start, discrete, mapping to stepSize per step)
 *
 * Matrix layout (conceptual):
 *
 *                 column index -->    0        1        2        3       ...     N
 *                 time step    -->    t0     t0+dt    t0+2dt   t0+3dt    ...     tN
 *
 *     row 0 : HEART_RATE.P   ->   [ hrP_0 ] [ hrP_1 ] [ hrP_2 ] [ hrP_3 ] ... [ hrP_N ]
 *     row 1 : HEART_RATE.I   ->   [ hrI_0 ] [ hrI_1 ] [ hrI_2 ] [ hrI_3 ] ... [ hrI_N ]
 *     row 2 : HEART_RATE.D   ->   [ hrD_0 ] [ hrD_1 ] [ hrD_2 ] [ hrD_3 ] ... [ hrD_N ]
 *     row 3 : DISTANCE.P     ->   [ dP_0  ] [ dP_1  ] [ dP_2  ] [ dP_3  ] ... [ dP_N  ]
 *     row 4 : DISTANCE.I     ->   [ dI_0  ] [ dI_1  ] [ dI_2  ] [ dI_3  ] ... [ dI_N  ]
 *     row 5 : DISTANCE.D     ->   [ dD_0  ] [ dD_1  ] [ dD_2  ] [ dD_3  ] ... [ dD_N  ]
 *     ...
 *     (one row per FeatureID = metric x PID component)
 *
 * Reading the matrix:
 *
 * - <code>get(featureID, k)</code>      → value of one feature at timestep k
 * - <code>getMutableRow(featureID)</code> → mutable view of one full feature row
 * - <code>allFeaturesAt(k)</code>       → copy of one full column (values of all features at timestep k)
 *
 * Terminology:
 *
 * - <b>stepCount</b>  = number of populated timesteps (columns currently containing data)
 * - <b>capacityInSteps</b> = internal column capacity; may be larger than stepCount
 *
 * Capacity management:
 *
 * The matrix grows automatically only when stepCount would exceed capacityInSteps.
 * When growth happens, a new larger matrix is allocated and existing values are copied.
 * This allows efficient appending without constantly reallocating memory.
 *
 * Each feature is identified by a {@link FeatureID}, which is a combination of
 * a {@link TimeSeriesMetric} and a {@link PIDComponent}.
 *
 * @param timeStart The timestamp of timestep 0 for all contained time series.
 * @param initialCapacityInSteps Initial internal storage capacity (column count) before reallocation is needed.
 */
class MultiTimeSeriesDiscrete(val timeStart: Instant, initialCapacityInSteps: Int = 512) {
    data class FeatureID(val metric: TimeSeriesMetric, val component: PIDComponent)

    // ---- internal state tracking ----
    private var stepCount: Int = 0
    private var capacityInSteps: Int = initialCapacityInSteps
    private var featureMatrix: D2Array<Double> = mk.zeros(featureCount, capacityInSteps)

    // ---- general state / time queries ----
    fun stepCount(): Int = stepCount
    private fun setStepCount(length: Int) {
        this.stepCount = length
    }

    fun getAllFeatureIDs(): Set<FeatureID> = featureIndexMap.keys
    fun getDuration(): Duration = stepSize * stepCount

    /**
     * Calculates the absolute timestamp for a given sample index.
     *
     * @param index The zero-based index of the time step. Must be a valid index within `0..<stepCount`.
     * @return The calculated [Instant] corresponding to the sample index.
     * @throws IllegalArgumentException if the `index` is out of bounds.
     */
    fun getSampleInstant(index: Int): Instant {
        require(index in 0..<stepCount) { "Sample index $index out of bounds 0..<$stepCount" }

        return timeStart + stepSize * index
    }

    /**
     * Retrieves the value of a specific feature at a given time step.
     *
     * This operator allows accessing data using bracket notation, e.g., `series[featureID, step]`.
     *
     * @param featureID The identifier for the feature (e.g., HEART_RATE.P) whose value is to be retrieved.
     * @param step The zero-based index of the time step. Must be a valid index within `0..<stepCount`.
     * @return The [Double] value of the feature at the specified time step.
     * @throws IllegalArgumentException if the `step` index is out of bounds or `featureID` is unknown.
     */// ---- data queries ----
    operator fun get(featureID: FeatureID, step: Int): Double {
        require(step in 0..<stepCount) { "Sample index $step out of bounds 0..<$stepCount" }
        val featureView = getMutableRow(featureID)
        return featureView[step]
    }

    /**
     * Retrieves a snapshot of all feature values at a specific time step.
     *
     * This function extracts a single column from the internal feature matrix, representing
     * the values of all features (e.g., HEART_RATE.P, DISTANCE.I, etc.) at the given discrete
     * time step. The result is a new 1D array, effectively a vertical slice of the time series data.
     *
     * Note: This operation creates a copy of the data.
     *
     * @param step The zero-based index of the time step (column) to retrieve.
     * @return A new [D1Array] containing the values of all features at the specified `step`.
     * @throws IllegalArgumentException if `step` is out of the valid range `0..<stepCount`.
     */
    fun allFeaturesAt(step: Int): D1Array<Double> {
        require(step in 0..<stepCount) { "Sample index $step out of bounds 0..<$stepCount" }

        // copy column at `index` into a new array, gives a sample of all features at one timestep
        val sample = DoubleArray(featureCount) { row ->
            featureMatrix[row, step]
        }

        return mk.ndarray(sample)
    }

    /**
     * Retrieves a mutable view of an entire row for a specific feature.
     *
     * This function provides direct, mutable access to the time series data for a single feature
     * (e.g., all heart rate "P" component values over time). The returned array is a **view**,
     * not a copy, meaning any modifications made to it will directly alter the data in the
     * underlying matrix. This is highly efficient for in-place updates.
     *
     * @param featureID The identifier for the feature row to retrieve (e.g., `FeatureID(HEART_RATE, P)`).
     * @return A mutable 1D array (`NDArray<Double, D1>`) representing the feature's data across all time steps.
     * @throws IllegalArgumentException if the `featureID` is not found in the time series.
     */
    fun getMutableRow(featureID: FeatureID): NDArray<Double, D1> {
        val row = featureIndexMap[featureID]
            ?: throw IllegalArgumentException("Unknown feature: $featureID")

        //no copying - multi gives a mutable view into the matrix row here
        return featureMatrix[row] as NDArray<Double, D1>
    }

    /**
     * Appends new time steps to the end of the time series.
     *
     * This method efficiently adds new columns of data to the internal `featureMatrix`.
     * It first ensures there is enough capacity to hold the new data, potentially reallocating
     * and copying the existing matrix if needed. It then copies the `newSamples` into the
     * space immediately following the current data and updates the `stepCount`.
     *
     * @param newSamples A 2D array containing the new data to append. Its shape must be
     *   `[featureCount, newSteps]`, where `newSteps` is the number of time steps to add.
     *   The rows must correspond to the features in the same order as the internal matrix.
     * @throws IllegalArgumentException if the number of features (rows) in `newSamples`
     *   does not match the `featureCount` of this time series.
     */
    private fun append(newSamples: D2Array<Double>) {
        val newSteps = newSamples.shape[1]
        if (newSteps == 0) return

        require(newSamples.shape[0] == featureCount) {
            "Expected ${featureCount} features, got ${newSamples.shape[0]}"
        }

        ensureCapacity(stepCount + newSteps);

        // Copy new samples
        for (row in 0 until featureCount) {
            for (col in 0 until newSteps) {
                featureMatrix[row, stepCount + col] = newSamples[row, col]
            }
        }

        stepCount += newSteps
    }

    /**
     * Ensures that the internal feature matrix has enough capacity to store at least `minimumSteps` time steps.
     *
     * If the current capacity is already sufficient, this method does nothing. Otherwise, it allocates
     * a new, larger matrix and copies the existing data into it. The new capacity will be the larger of
     * either double the current capacity or the `minimumSteps` required, providing an amortized
     * constant time for append operations.
     *
     * @param minimumSteps The minimum number of time steps that must be accommodatable.
     */
    fun ensureCapacity(minimumSteps: Int) {
        if (minimumSteps <= capacityInSteps) return;

        val newCapacity = maxOf(capacityInSteps * 2, minimumSteps)
        val updated = mk.zeros<Double>(featureCount, newCapacity)

        // Copy existing data
        for (row in 0 until featureCount) {
            for (col in 0 until stepCount) {
                updated[row, col] = featureMatrix[row, col]
            }
        }

        featureMatrix = updated
        capacityInSteps = newCapacity
    }

    companion object {
        private val featureCount: Int =
            TimeSeriesMetric.entries.sumOf { it.signalClass.components.size }
        private val stepSize = Predictor.TIME_SERIES_STEP_DURATION

        /**
         * A map that links a unique feature identifier ([FeatureID]) to its corresponding row index
         * in the [featureMatrix].
         *
         * This map is a critical lookup table for quickly finding the correct row for a given
         * metric (e.g., `HEART_RATE`) and component (e.g., `P`). It is initialized once by
         * iterating through all defined [TimeSeriesMetric] entries and their associated
         * [PIDComponent]s, assigning a unique, sequential integer index to each combination.
         *
         * For example:
         * - `FeatureID(HEART_RATE, P)` -> `0`
         * - `FeatureID(HEART_RATE, I)` -> `1`
         * - `FeatureID(HEART_RATE, D)` -> `2`
         * - `FeatureID(DISTANCE, P)`   -> `3`
         * - ... and so on for more featuresIDs ...
         *
         * This allows for efficient, `O(1)` access to a feature's time series data row within the matrix.
         */
        private val featureIndexMap: Map<FeatureID, Int> = TimeSeriesMetric.values()
            .map { metric ->
                metric.signalClass.components.map { component ->
                    FeatureID(
                        metric,
                        component
                    )
                }
            }.flatten().mapIndexed { index, featureID -> featureID to index }.toMap()

        /**
         * Creates a [MultiTimeSeriesDiscrete] instance from raw, continuous time series data.
         *
         * This factory function performs the complete preprocessing pipeline:
         * 1.  It takes a [Predictor.MultiTimeSeriesEntries] object containing lists of raw data points
         *     (e.g., heart rate, distance) with their timestamps.
         * 2.  For each [TimeSeriesMetric] (like `HEART_RATE`, `DISTANCE`), it discretizes the raw data
         *     into a uniformly sampled time series. This creates the proportional (P) component.
         * 3.  It then computes the other feature components (e.g., integral (I), derivative (D))
         *     from the discretized proportional data.
         * 4.  Finally, it populates a new [MultiTimeSeriesDiscrete] instance, organizing all computed
         *     feature components into the 2D matrix structure.
         *
         * The resulting object is aligned, uniformly sampled, and ready for use as model input.
         * All generated time series share the same start time and step size.
         *
         * @param raw An object containing the raw, non-uniform time series data points.
         * @return A fully populated [MultiTimeSeriesDiscrete] instance.
         */
        fun fromEntries(raw: Predictor.MultiTimeSeriesEntries): MultiTimeSeriesDiscrete {
            if (TimeSeriesMetric.entries.isEmpty()) {
                return MultiTimeSeriesDiscrete(raw.timeStart, 0)
            }

            val mtsd = MultiTimeSeriesDiscrete(raw.timeStart)

            val stepCount = (raw.duration / Predictor.TIME_SERIES_STEP_DURATION).toInt();
            mtsd.ensureCapacity(stepCount);
            mtsd.setStepCount(stepCount)

            TimeSeriesMetric.entries.forEach { metric ->
                //IDEA: save another copy by passing a reference to the internal matrix to discretizeTimeSeries
                val discreteProportional = discretizeTimeSeries(
                    GenericTimedDataPointTimeSeries(
                        timeStart = raw.timeStart,
                        duration = raw.duration,
                        metric = metric,
                        data = when (metric) {
                            TimeSeriesMetric.HEART_RATE -> raw.heartRate.map(::GenericTimedDataPoint)
                            TimeSeriesMetric.DISTANCE -> raw.distance.map(::GenericTimedDataPoint)
                        }
                    ),
                    stepCount
                )

                require(discreteProportional.size == stepCount);

                metric.signalClass.components.forEach { component ->
                    val featureID = FeatureID(metric, component)
                    val componentData = featureID.component.compute(discreteProportional)
                    val featureView = mtsd.getMutableRow(featureID)
                    componentData.forEachIndexed { index, value -> featureView[index] = value }
                }
            }

            return mtsd
        }
    }
}
