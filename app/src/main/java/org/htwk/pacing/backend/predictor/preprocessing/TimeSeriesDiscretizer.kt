package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor.GenericTimedDataPoint
import org.htwk.pacing.ui.math.roundInstantToResolution
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object TimeSeriesDiscretizer {
    fun run_1_discretize(raw: Predictor.MultiTimeSeriesEntries): IPreprocessor.MultiTimeSeriesDiscrete {
        val timeStart = kotlinx.datetime.Clock.System.now() - 6.hours

        //HeartRate 10 min Buckets --> Durchschnitt von Bucket
        val groupedHR = raw.heartRate.groupBy { entry ->
            roundInstantToResolution(entry.time, 10.minutes)
        }

        val hrAveragesPer10Min = groupedHR.mapValues { (_, entries) ->
            entries.map { it.bpm.toDouble() }.average()
        }

        val sortedKeys = hrAveragesPer10Min.keys.sorted()
        val heartRateArray = DoubleArray(sortedKeys.size)
        var lastKnownHR: Double = hrAveragesPer10Min[sortedKeys.first()] ?: 0.0
        for ((i, key) in sortedKeys.withIndex()) {
            val avg = hrAveragesPer10Min[key]

            //fehlende Intervalle werden mit letztem bekannten wert aufgefüllt --> evtl. nicht optimal
            //durchschnitt zwischen letztem bekannten und nächtstem wert wäre besser
            if (avg != null) lastKnownHR = avg
            heartRateArray[i] = lastKnownHR
        }


        //Distance 10-Minuten-Buckets
        val groupedDistance = raw.distance.groupBy { entry ->
            roundInstantToResolution(entry.start, 10.minutes)
        }

        val distancePer10Min = groupedDistance.mapValues { (_, entries) ->
            entries.sumOf { it.length.inMeters() } // Gesamtstrecke pro Intervall
        }

        val distanceArray = DoubleArray(sortedKeys.size)
        for ((i, key) in sortedKeys.withIndex()) {
            val value = distancePer10Min[key]
            distanceArray[i] = value!!
        }

        return IPreprocessor.MultiTimeSeriesDiscrete(
            timeStart = timeStart,
            heartRate = DiscretePID(heartRateArray, doubleArrayOf(), doubleArrayOf()),
            distance = DiscreteIntegral(distanceArray)
        )
    }

    private fun interpolateMissingValues(values: Map<Instant, Float>): FloatArray {
        val sortedKeys = values.keys.sorted()
        val result = FloatArray(sortedKeys.size)
        val knownValues = sortedKeys.map { key -> values[key] }

        for (i in sortedKeys.indices) {
            val current = knownValues[i]
            if (current != null) {
                result[i] = current
            } else {
                val leftIndex = (i downTo 0).firstOrNull { knownValues[it] != null }
                val rightIndex = (i until knownValues.size).firstOrNull { knownValues[it] != null }
                val left = leftIndex?.let { knownValues[it]!! }
                val right = rightIndex?.let { knownValues[it]!! }

                result[i] = if (left != null && right != null && leftIndex != rightIndex) {
                    val t = (i - leftIndex).toFloat() / (rightIndex - leftIndex)
                    left + t * (right - left)
                } else {
                    left ?: right ?: 0f
                }
            }
        }
        return result
    }

    private fun discretizeTimeSeries(
        input: List<GenericTimedDataPoint>,
        now10min: Instant,
        holdEdges: Boolean = true // bei false: lin. Extrapolation
    ): DoubleArray {
        val size = Predictor.TIME_SERIES_SAMPLE_COUNT
        val duration = Predictor.TIME_SERIES_DURATION
        val start = now10min - duration

        val anchorMs = start.toEpochMilliseconds()

        //calculate average per step-size bucket (not adjusted, perhaps replace by time-weighted avg.)
        val averagesPerStepBucket: Map<Instant, Double> =
            input.groupBy { roundInstantToResolution(it.time, Predictor.TIME_SERIES_STEP_DURATION) }
                .mapValues { (_, xs) -> xs.map { it.value }.average() }

        //map average per bucket to array (1 array element corresponds to average of all values that fall into index * TIME_SERIES_STEP_DURATION)
        val p = DoubleArray(size) { Double.NaN }
        for (i in 0 until size) {
            val tMs = anchorMs + i.toLong() * stepMs
            avgByBucket[tMs]?.let { p[i] = it }
        }

        val known = (0 until size).filter { !p[it].isNaN() }
        if (known.isEmpty()) return DoubleArray(size) //return zero-array if no datapoints

        //linearly interpolate between datapoints
        var a = known.first()
        for (b in known.drop(1)) {
            val v0 = p[a];
            val v1 = p[b];
            val span = (b - a)
            for (i in a + 1 until b) p[i] = v0 + (v1 - v0) * ((i - a) / span)
            a = b
        }

        // boundaries: constant extrapolation
        for (i in 0 until known.first()) p[i] = p[known.first()] //extrapolate towards left
        for (i in known.last() + 1 until size) p[i] = p[known.last()] //extrapolate towards right

        return p
    }

    private fun discretizeWithMissingValues(
        startTime: Instant,
        entries: List<GenericTimedDataPoint>,
    ): DoubleArray {
        require(entries.isNotEmpty()) //we need at least one entry to work with

        val startTimeRounded =
            roundInstantToResolution(startTime, Predictor.TIME_SERIES_STEP_DURATION)

        //sort into buckets, where the key is the step-size interval each entry falls into
        val bucketsByStep = entries.groupBy { it ->
            roundInstantToResolution(it.time, Predictor.TIME_SERIES_STEP_DURATION)
        }

        // TODO: weighted resampling/average, because incoming HR data points are probably unevenly spaced
        val averagesPerBucket = bucketsByStep.mapValues { (_, group) ->
            group.map { it -> it.value }.average()
        }

        //we enter averages per time step into this array, steps with no corresponding entries are left at NaN
        val discreteWithGaps = DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { Double.NaN }

        //iterate through time-sorted averagesPerBucket (it.key is step-rounded time)
        averagesPerBucket.entries.sortedBy { it.key }.forEach { (time, value) ->
            val index = ((time - startTimeRounded) / Predictor.TIME_SERIES_STEP_DURATION).toInt();
            if (index in discreteWithGaps.indices) {
                discreteWithGaps[index] = value
            }
        }

        return discreteWithGaps
    }
}