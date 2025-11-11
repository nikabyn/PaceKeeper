package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration
import kotlin.time.times
import org.htwk.pacing.backend.database.Length

object FallbackHandler {

    /**
     * Ensures that the provided multi-time series data contains entries for both heart rate and distance.
     * If either of the series in the input `raw` data is empty, this function attempts to fill it
     * by first looking for historical data, and then by generating a default series as a last resort.
     *
     * This acts as a primary entry point for the fallback mechanism, delegating the specific logic for
     * heart rate and distance to `ensureHeartRateData` and `ensureDistanceData` respectively.
     *
     * @param raw The initial `Predictor.MultiTimeSeriesEntries` which may have empty lists for its series.
     * @return A new `Predictor.MultiTimeSeriesEntries` instance with guaranteed non-empty data series.
     * @throws IllegalStateException if data cannot be provided for any series, even after all fallback attempts.
     */
    fun ensureData(raw: Predictor.MultiTimeSeriesEntries): Predictor.MultiTimeSeriesEntries {
        val hr = ensureHeartRateData(raw.heartRate, raw.timeStart, raw.duration)
        val dist = ensureDistanceData(raw.distance, raw.timeStart, raw.duration)

        return Predictor.MultiTimeSeriesEntries(
            timeStart = raw.timeStart,
            duration = raw.duration,
            heartRate = hr,
            distance = dist
        )
    }

    /**
     * Ensures that a list of [HeartRateEntry] is available for the given time period.
     *
     * This function implements a fallback mechanism to provide heart rate data if the initial
     * raw data is empty. The fallback order is as follows:
     * 1.  Use the provided `raw` data if it's not empty.
     * 2.  Attempt to load historical heart rate data for the specified time range.
     * 3.  Generate a default, constant heart rate series if no historical data is found.
     *
     * If all methods fail to produce data (which is unlikely as the default generation should always succeed),
     * it throws an exception.
     *
     * @param raw The initial list of heart rate entries, which might be empty.
     * @param timeStart The start time of the time series.
     * @param duration The total duration of the time series.
     * @return A non-empty list of [HeartRateEntry] for the specified period.
     * @throws IllegalStateException if no heart rate data can be provided by any of the fallback mechanisms.
     */
    private fun ensureHeartRateData(
        raw: List<HeartRateEntry>,
        timeStart: Instant,
        duration: Duration
    ): List<HeartRateEntry> {
        if (raw.isNotEmpty()) return raw

        val history = loadHistoricalHeartRateData(timeStart, duration)
        if (history.isNotEmpty()) return history

        val default = generateDefaultHeartRateSeries(timeStart, duration)
        if (default.isNotEmpty()) return default

        throw IllegalStateException("no heartrate data available – no prediction possible.")
    }

    /**
     * Ensures that a list of distance data is available for a given time period.
     *
     * This function implements a fallback mechanism to provide distance data if the initial `raw` list is empty.
     * The fallback order is as follows:
     * 1. If `raw` is not empty, it is returned immediately.
     * 2. Tries to load historical distance data for the specified time range.
     * 3. If no historical data is found, generates a default series with zero distance covered.
     * 4. If all attempts fail, it throws an [IllegalStateException].
     *
     * @param raw The initial list of distance entries, which might be empty.
     * @param timeStart The start time of the period for which data is required.
     * @param duration The duration of the period.
     * @return A non-empty list of [DistanceEntry] objects.
     * @throws IllegalStateException if no distance data can be provided (i.e., raw, historical, and default are all empty).
     */
    private fun ensureDistanceData(
        raw: List<DistanceEntry>,
        timeStart: Instant,
        duration: Duration
    ): List<DistanceEntry> {
        if (raw.isNotEmpty()) return raw

        val history = loadHistoricalDistanceData(timeStart, duration)
        if (history.isNotEmpty()) return history

        val default = generateDefaultDistanceSeries(timeStart, duration)
        if (default.isNotEmpty()) return default

        throw IllegalStateException("no distance data available – no prediction possible.")
    }

    private fun loadHistoricalHeartRateData(start: Instant, duration: Duration): List<HeartRateEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadHistoricalDistanceData(start: Instant, duration: Duration): List<DistanceEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    /**
     * Generates a default, constant heart rate time series if no other data is available.
     *
     * This function serves as a last-resort fallback. It creates a list of [HeartRateEntry]
     * objects with a fixed BPM value (currently 75L) for the entire specified duration. The time
     * series is generated at regular intervals defined by [Predictor.TIME_SERIES_STEP_DURATION].
     *
     * @param start The [Instant] at which the time series should begin.
     * @param duration The total [Duration] for which the time series should be generated.
     * @return A [List] of [HeartRateEntry] objects representing the default heart rate data.
     */

    //The daily curve should be applied using a neutral average over the time period to generate more realistic values
    //defaultBpm needs to be dynamic
    private fun generateDefaultHeartRateSeries(start: Instant, duration: Duration): List<HeartRateEntry> {
        val step = Predictor.TIME_SERIES_STEP_DURATION
        val points = (duration / step).toInt()
        val defaultBpm = 75L
        return List(points) { i -> HeartRateEntry(start + (i * step), defaultBpm) }
    }

    /**
     * Generates a default time series for distance data as a fallback.
     *
     * This function is used when no actual or historical distance data is available. It creates a list
     * of [DistanceEntry] objects, each representing a time step of a fixed duration
     * ([Predictor.TIME_SERIES_STEP_DURATION]). For each step, the distance covered is set to a default
     * value of 0.0, effectively representing a stationary state.
     *
     * @param start The [Instant] when the time series begins.
     * @param duration The total [Duration] of the time series to generate.
     * @return A [List] of [DistanceEntry] objects with a distance of 0.0 for each time step.
     */

    //default length could be changed
    private fun generateDefaultDistanceSeries(start: Instant, duration: Duration): List<DistanceEntry> {
        val step = Predictor.TIME_SERIES_STEP_DURATION
        val points = (duration / step).toInt()
        val defaultLength = 0.0
        return List(points) { i ->
            DistanceEntry(
                start + (i * step),
                start + ((i + 1) * step),
                Length(defaultLength)
            )
        }
    }
}