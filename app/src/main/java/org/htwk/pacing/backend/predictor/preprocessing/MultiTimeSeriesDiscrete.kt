package org.htwk.pacing.backend.predictor.preprocessing

import androidx.annotation.IntRange
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete.Companion.featureCount
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete.Companion.stepDuration
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
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import kotlin.time.Duration


/**
 * A container for multiple, aligned, discrete time series, optimized for model input.
 *
 * All time series share the same starting timestamp [timeStart] and are sampled using a
 * fixed interval [stepDuration]. Each metric can have multiple feature components (e.g. P/I/D)
 * and each of these becomes one row in a shared 2D matrix. Columns correspond to discrete
 * time steps
 *
 *     Rows    = Features  (metric x PID component)
 *     Columns = Timesteps (from [timeStart], discrete, mapping to [stepDuration] per step)
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
 * - `get(featureID, k)` -> value of one feature at timestep k
 * - `getMutableRow(featureID)` -> mutable view of one full feature row
 * - `allFeaturesAt(k)` -> copy of one full column (values of all features at timestep k)
 *
 * Terminology:
 *
 * - [stepCount] = number of populated timesteps (columns currently containing data)
 * - [capacityInSteps] = internal column capacity; may be larger than stepCount
 *
 * Capacity management:
 *
 * The matrix grows automatically only when [stepCount] would exceed [capacityInSteps].
 * When growth happens, a new larger matrix is allocated and existing values are copied.
 * This allows efficient appending without constantly reallocating memory.
 *
 * Each feature is identified by a [FeatureID], which is a combination of
 * a [TimeSeriesMetric] and a [FeatureComponent].
 *
 * @param timeStart The timestamp of timestep 0 for all contained time series.
 * @param initialCapacityInSteps Initial internal storage capacity (column count) before reallocation is needed.
 */
class MultiTimeSeriesDiscrete(val timeStart: Instant, initialCapacityInSteps: Int = 512) {
    data class FeatureID(val metric: TimeSeriesMetric, val component: FeatureComponent)

    // ---- internal state tracking ----
    private var stepCount: Int = 0
    private var capacityInSteps: Int = initialCapacityInSteps
    private var featureMatrix: D2Array<Double> = mk.zeros(featureCount, capacityInSteps)

    // ---- general state / time queries ----
    fun stepCount(): Int = stepCount
    private fun resize(newStepCount: Int) {
        reserveCapacity(newStepCount)
        this.stepCount = newStepCount
    }

    fun getAllFeatureIDs(): Set<FeatureID> = featureIndexMap.keys
    fun getDuration(): Duration = stepDuration * stepCount

    /**
     * Calculates the absolute timestamp for a given sample index.
     *
     * @param index The zero-based index of the time step. Must be a valid index within `0..<stepCount`.
     * @return The calculated [Instant] corresponding to the sample index.
     * @throws IllegalArgumentException if the `index` is out of bounds.
     */
    fun getSampleInstant(@IntRange(from = 0) index: Int): Instant {
        require(index < stepCount) { "Sample index $index out of bounds 0..<$stepCount" }

        return timeStart + stepDuration * index
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
    operator fun get(featureID: FeatureID, @IntRange(from = 0) step: Int): Double {
        require(step < stepCount) { "Sample index $step out of bounds 0..<$stepCount" }
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
    fun allFeaturesAt(@IntRange(from = 0) step: Int): D1Array<Double> {
        require(step < stepCount) { "Sample index $step out of bounds 0..<$stepCount" }

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
        return featureMatrix[row].slice(0 until stepCount)
    }

    /**
     * Appends new time steps to the end of the time series.
     *
     * This method efficiently adds new columns of data to the internal [featureMatrix].
     * It first ensures there is enough capacity to hold the new data, potentially reallocating
     * and copying the existing matrix if needed. It then copies the `newSamples` into the
     * space immediately following the current data and updates the [stepCount].
     *
     * @param newSamples A 2D array containing the new data to append. Its shape must be
     *   [featureCount, newSteps], where `newSteps` is the number of time steps to add.
     *   The rows must correspond to the features in the same order as the internal matrix.
     * @throws IllegalArgumentException if the number of features (rows) in [newSamples]
     *   does not match the [featureCount] of this time series.
     */
    private fun append(newSamples: D2Array<Double>) {
        val newSteps = newSamples.shape[1]
        if (newSteps == 0) return

        require(newSamples.shape[0] == featureCount) {
            "Expected $featureCount features, got ${newSamples.shape[0]}"
        }

        reserveCapacity(stepCount + newSteps)

        // Copy new samples
        for (row in 0 until featureCount) {
            for (col in 0 until newSteps) {
                featureMatrix[row, stepCount + col] = newSamples[row, col]
            }
        }

        stepCount += newSteps
    }

    /**
     * Ensures that the internal feature matrix has enough capacity to store at least [minimumSteps] time steps.
     *
     * If the current capacity is already sufficient, this method does nothing. Otherwise, it allocates
     * a new, larger matrix and copies the existing data into it. The new capacity will be the larger of
     * either double the current capacity or the [minimumSteps] required, providing an amortized
     * constant time for append operations.
     *
     * @param minimumSteps The minimum number of time steps that must be accommodatable.
     */
    private fun reserveCapacity(minimumSteps: Int) {
        if (minimumSteps <= capacityInSteps) return

        //next-highest power of two relative to minimumSteps
        val newCapacity = minimumSteps.takeHighestOneBit() shl 1
        if (newCapacity > MAX_CAPACITY) { //limit allocation for unreasonably large grows
            throw IllegalArgumentException(
                "Requested capacity $newCapacity exceeds maximum capacity of $MAX_CAPACITY"
            )
        }
        //create new matrix/buffer
        val updated = mk.zeros<Double>(featureCount, newCapacity)

        //copy data from old matrix/buffer to new
        for (row in 0 until featureCount) {
            for (col in 0 until stepCount) {
                updated[row, col] = featureMatrix[row, col]
            }
        }

        featureMatrix = updated
        capacityInSteps = newCapacity
    }

    companion object {
        private const val MAX_CAPACITY = 1 shl 24 // e.g. 16_777_216 steps, still a very chill limit
        private val stepDuration = Predictor.TIME_SERIES_STEP_DURATION

        /**
         * A map that links a unique feature identifier ([FeatureID]) to its corresponding row index
         * in the [featureMatrix].
         *
         * This map is a critical lookup table for quickly finding the correct row for a given
         * metric (e.g., `HEART_RATE`) and component (e.g., `P`). It is initialized once by
         * iterating through all defined [TimeSeriesMetric] entries and their associated
         * [FeatureComponent]s, assigning a unique, sequential integer index to each combination.
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
        private val featureIndexMap: Map<FeatureID, Int> = TimeSeriesMetric.entries
            .map { metric ->
                metric.signalClass.components.map { component ->
                    FeatureID(
                        metric,
                        component
                    )
                }
            }.flatten().mapIndexed { index, featureID -> featureID to index }.toMap()

        private val featureCount: Int = featureIndexMap.size

        fun fromSubSlice(input: MultiTimeSeriesDiscrete, indexStart: Int, indexEnd: Int) : MultiTimeSeriesDiscrete {
            require(indexStart >= 0 && indexEnd < input.stepCount)

            val newSteps: Int = (indexEnd - indexStart)

            val newMTSD = MultiTimeSeriesDiscrete(timeStart = input.timeStart, initialCapacityInSteps = newSteps)
            newMTSD.stepCount = newSteps

            newMTSD.featureMatrix =
                (input.featureMatrix[0 until featureCount, indexStart until indexEnd] as D2Array<Double>)

            return newMTSD
        }

        /**
         * Creates a [MultiTimeSeriesDiscrete] instance from raw, continuous time series data.
         *
         * This factory function performs the complete preprocessing pipeline:
         * 1.  It takes a [Predictor.MultiTimeSeriesEntries] object containing lists of raw data points
         *     (e.g., heart rate, distance) with their timestamps.
         * 2.  For each [TimeSeriesMetric] (like `HEART_RATE`, `DISTANCE`), it discretizes the raw data
         *     into a [GenericTimedDataPointTimeSeries]. This creates the proportional (P) component.
         * 3.  It then computes the other feature components (e.g., integral (I), derivative (D))
         *     from the discretized proportional data.
         * 4.  Populates a new [MultiTimeSeriesDiscrete] instance, organizing all computed
         *     feature components into the 2D matrix structure.
         * 5.  Performs final adjustments for heart rate (HR) and heart rate variability (HRV) using [adjustHR]
         *     and [adjustHRV] based on the provided [Predictor.FixedParameters].
         *
         * The resulting object is aligned, uniformly sampled, and ready for use as model input.
         * All generated time series share the same start time and step size.
         *
         * @param raw An object containing the raw, non-uniform time series data points.
         * @param fixedParameters Data class containing human physiological variables that are (difficult to) change
         * @return A fully populated [MultiTimeSeriesDiscrete] instance.
         */
        fun fromEntries(raw: Predictor.MultiTimeSeriesEntries, fixedParameters: Predictor.FixedParameters): MultiTimeSeriesDiscrete {
            if (TimeSeriesMetric.entries.isEmpty()) {
                return MultiTimeSeriesDiscrete(raw.timeStart, 0)
            }

            val multiTimeSeriesDiscrete = MultiTimeSeriesDiscrete(raw.timeStart)

            val stepCount = (raw.duration / Predictor.TIME_SERIES_STEP_DURATION).toInt()
            multiTimeSeriesDiscrete.resize(stepCount)

            val metricCounts = mutableMapOf<TimeSeriesMetric, Int>()

            TimeSeriesMetric.entries.forEach { metric ->
                //IDEA: save another copy by passing a reference to the internal matrix to discretizeTimeSeries

                val singleDiscreteTimeSeries = discretizeTimeSeries(
                    ensureData(
                        id = metric.ordinal,
                        buildGenericTimeSeries(metric, raw)
                            .also { series ->
                                // count how many entries we got
                                metricCounts[metric] = series.data.size
                            }
                    ),
                    targetLength = stepCount,
                    //we have to use constant interpolation to prevent data leakage
                    //see comment in InterpolationMode definition
                    interpolationMode = TimeSeriesDiscretizer.InterpolationMode.CONSTANT
                )

                val discreteProportional = singleDiscreteTimeSeries.values

                //daily standard curve
                /*for(i in 0 until discreteProportional.size) {
                    discreteProportional[i % 48] += discreteProportional[i]
                }*/

                require(discreteProportional.size == stepCount);

                metric.signalClass.components.forEach { component ->
                    val featureID = FeatureID(metric, component)
                    val componentData = featureID.component.compute(discreteProportional)
                    val featureView = multiTimeSeriesDiscrete.getMutableRow(featureID)

                    componentData.forEachIndexed { index, value ->
                        featureView[index] = value
                    }
                }
            }

            val debug = metricCounts.entries
                .sortedBy { it.key.ordinal } //keep defined enum order in output
                .joinToString(", ") { (metric, count) ->
                    "${metric.name}=$count"
                }

            println("per-metric input entry counts (before ensureData): $debug")

            val mutableHeartRateArray = multiTimeSeriesDiscrete.getMutableRow(FeatureID(TimeSeriesMetric.HEART_RATE,
                FeatureComponent.PROPORTIONAL))

            val mutableHRVArray = multiTimeSeriesDiscrete.getMutableRow(FeatureID(TimeSeriesMetric.HEART_RATE_VARIABILITY,
                FeatureComponent.PROPORTIONAL))

            mutableHeartRateArray.indices.forEach { i ->
                val hr = mutableHeartRateArray[i]
                val hrv = mutableHRVArray[i]
                mutableHeartRateArray[i] = adjustHR(hr, fixedParameters)
                mutableHRVArray[i] = adjustHRV(hr, hrv, fixedParameters)
            }

            return multiTimeSeriesDiscrete
        }
    }
}

/**
 * Creates a generic time series (`GenericTimedDataPointTimeSeries`) from the raw
 * MultiTimeSeries data of a `Predictor` object for the specified metric.
 *
 * The function automatically selects the appropriate data series from [raw] based on the
 * passed [metric] and converts each entry into a [GenericTimedDataPoint].
 * In addition, the metadata `timeStart`, `duration`, and the continuity of the metric
 * are included in the result.
 *
 * @param metric The metric for which the time series is to be created. Determines which
 *               list of `raw` is used (e.g., heart rate, steps, speed).
 * @param raw The raw MultiTimeSeries data containing all possible time series.
 *
 * @return A [GenericTimedDataPointTimeSeries] object that:
 *  - contains the mapped data points (`GenericTimedDataPoint`),
 *  - adopts the start time (`timeStart`) and total duration (`duration`) of the time series,
 *  - specifies whether the metric is continuous (`isContinuous`).
 */
internal fun buildGenericTimeSeries(
    metric: TimeSeriesMetric,
    raw: Predictor.MultiTimeSeriesEntries
): GenericTimedDataPointTimeSeries {

    val data = when (metric) {
        TimeSeriesMetric.HEART_RATE ->
            raw.heartRate.map(::GenericTimedDataPoint)

        TimeSeriesMetric.DISTANCE ->
            raw.distance.map(::GenericTimedDataPoint)

        TimeSeriesMetric.ELEVATION_GAINED ->
            raw.elevationGained.map(::GenericTimedDataPoint)

        TimeSeriesMetric.SKIN_TEMPERATURE ->
            raw.skinTemperature.map(::GenericTimedDataPoint)

        TimeSeriesMetric.HEART_RATE_VARIABILITY ->
            raw.heartRateVariability.map (::GenericTimedDataPoint)

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
