package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object Preprocessor : IPreprocessor {

    /**
     * A generic data structure to unify different time series data types
     * before passing them to the common processing functions.
     */
    private data class GenericTimedDataPoint(
        val time: Instant,
        val value: Double,
    )

    /**
     * Processes continuous time series data, like heart rate.
     * @param input The list of timed data points.
     * @param now10min The reference start time for discretization.
     * @return A [DiscretePID] object containing the discretized series and its derivatives/integrals.
     */
    private fun processContinuous(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
    ): DiscretePID {
        val p = discretizeTimeSeries(timeStart + TIME_SERIES_DURATION, input)
        //TODO: implement functions for discrete integral, derivative, use them here
        return DiscretePID(p, doubleArrayOf(), doubleArrayOf())
    }

    /**
     * Processes aggregated/counted time series data, like step count.
     * @param input The list of timed data points.
     * @param now10min The reference start time for discretization.
     * @return A [DiscreteIntegral] object containing the discretized series derivative.
     */
    private fun processAggregated(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
    ): DiscreteIntegral {
        val p = discretizeTimeSeries(timeStart + TIME_SERIES_DURATION, input)
        //TODO: implement function for discrete derivative, use it here
        return DiscreteIntegral(doubleArrayOf())
    }

    //class 3) (unused for now), see ui#38
    private fun processDailyConstant(): Double {
        return 0.0;
    }

    /**
     * Resamples a time series to a uniform time grid.
     *
     * This function converts arbitrarily timed data points into a uniformly spaced series
     * starting at `timeStart`, with a fixed `step` duration.
     *
     * @param timeStart The start time for the resampling grid.
     * @param input The list of `GenericTimedDataPoint`s to resample. Must not be empty.
     * @param step The time interval for the output grid.
     * @param holdEdges If `true`, uses constant extrapolation for values outside the input time range.
     * @return A `DoubleArray` with the resampled time series values.
     * @throws IllegalArgumentException if `input` is empty.
     */
    private fun discretizeTimeSeries(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
        step: Duration = 10.minutes,
        holdEdges: Boolean = true // bei false: lin. Extrapolation
    ): DoubleArray {
        //constant extrapolation of first value in time series
        require(input.isNotEmpty());

        //TODO: replace with actual resampling code (this is just a placeholder for a constant fill)
        return DoubleArray((TIME_SERIES_DURATION.inWholeHours * 6).toInt()) { input[0].value };
    }

    fun run_clean_data(raw: Predictor.MultiTimeSeriesEntries): IPreprocessor.MultiTimeSeriesDiscrete {
        // generic cleaning of data
        fun <T, R : Comparable<R>> cleanData(
            list: List<T>,
            sortKey: (T) -> R,
            isInvalid: (T) -> Boolean,
            replaceInvalid: (T) -> T,
            distinctByKey: ((T) -> Any)? = null
        ): Pair<MutableList<T>, Double> {
            var correctionCount = 0

            val cleanedList = list
                .sortedBy(sortKey)
                .let { if (distinctByKey != null) it.distinctBy(distinctByKey) else it.distinct() }
                .map {
                    if (isInvalid(it)) {
                        correctionCount++
                        replaceInvalid(it)
                    } else it
                }
                .toMutableList()

            val correctionRatio = if (cleanedList.isNotEmpty()) {
                correctionCount.toDouble() / cleanedList.size
            } else 0.0

            return Pair(cleanedList, correctionRatio)
        }

        val (cleanedHeartRates, correctionHeartRatio) = cleanData(
            list = raw.heartRate,
            sortKey = { it.time },
            isInvalid = { it.bpm < 30 || it.bpm > 220 },
            replaceInvalid = { HeartRateEntry(it.time, 0) },
            distinctByKey = { it.time } // entfernt Duplikate basierend auf Start + End
        )

        val (cleanedDistances, correctionDistancesRatio) = cleanData(
            list = raw.distance,
            sortKey = { it.start },
            isInvalid = { it.length.inMeters() < 0 },
            replaceInvalid = { DistanceEntry(it.start, it.end, length = Length(0.0)) },
            distinctByKey = { it.start to it.end } // entfernt Duplikate basierend auf Start + End
        )

        // TODO: see other ticket
        return Predictor.MultiTimeSeriesSamples(
            timeStart = Clock.System.now() - 6.hours,
            heartRate = cleanedHeartRates.map { it.bpm.toFloat() }.toFloatArray(),
            distance = cleanedDistances.map { it.length.inMeters().toFloat() }.toFloatArray(),
            cleanedHeartRatesRatio = Percentage(correctionHeartRatio),
            cleanedDistancesRatio = Percentage(correctionDistancesRatio)

        )
    }
}