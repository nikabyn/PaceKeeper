package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration
import kotlin.time.times
import org.htwk.pacing.backend.database.Length

object FallbackHandler {

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

    // The daily curve should be applied using a neutral average over the time period to generate more realistic values
    //defaultBpm needs to be dynamic
    private fun generateDefaultHeartRateSeries(start: Instant, duration: Duration): List<HeartRateEntry> {
        val step = Predictor.TIME_SERIES_STEP_DURATION
        val points = (duration / step).toInt()
        val defaultBpm = 75L
        return List(points) { i -> HeartRateEntry(start + (i * step), defaultBpm) }
    }

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
