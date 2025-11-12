package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.htwk.pacing.backend.database.Length

class FallbackHandlerTest {

    private val timeStart = Instant.fromEpochMilliseconds(0)
    private val duration = 60.minutes

    @Test
    fun ensureDataGeneratesDefaultHeartRateAndDistanceIfEmpty() {
        val raw = Predictor.MultiTimeSeriesEntries(timeStart, duration, emptyList(), emptyList())

        val result = FallbackHandler.ensureDataFallback(raw)

        // HeartRate Defaults
        assertEquals(6, result.heartRate.size)
        assertTrue(result.heartRate.all { it.bpm == 75L })

        // Distance Defaults
        assertEquals(6, result.distance.size)
        val totalDistance = result.distance.sumOf { it.length.inMeters() }
        val expectedDistance = 6 * 8.0
        assertEquals(expectedDistance, totalDistance, 0.0)

    }

    @Test
    fun ensureDataReturnsRawDataIfPresent() {
        val hr = listOf(
            HeartRateEntry(timeStart, 80),
            HeartRateEntry(timeStart + 10.minutes, 82)
        )
        val dist = listOf(
            DistanceEntry(timeStart, timeStart + 10.minutes, Length(0.0)),
            DistanceEntry(timeStart + 10.minutes, timeStart + 20.minutes, Length(50.0))
        )
        val raw = Predictor.MultiTimeSeriesEntries(timeStart, duration, hr, dist)

        val result = FallbackHandler.ensureDataFallback(raw)

        assertEquals(hr, result.heartRate)
        assertEquals(dist, result.distance)
    }
}