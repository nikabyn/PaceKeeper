package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.htwk.pacing.backend.database.Length

class FallbackHandlerTest {

    private val timeStart = Clock.System.now()
    private val duration = 60.minutes

    @Test
    fun ensureData_generatesDefaultHeartRateAndDistanceIfEmpty() {
        val raw = Predictor.MultiTimeSeriesEntries(timeStart, duration, emptyList(), emptyList())

        val result = FallbackHandler.ensureData(raw)

        // HeartRate Defaults
        assertEquals(6, result.heartRate.size) // 60min / 10min step
        assertEquals(75L, result.heartRate.first().bpm)

        // Distance Defaults
        assertEquals(6, result.distance.size)
        assertEquals(0.0, result.distance.first().length.inMeters(), 0.0)
    }

    @Test
    fun ensureData_returnsRawDataIfPresent() {
        val hr = listOf(
            HeartRateEntry(timeStart, 80),
            HeartRateEntry(timeStart + 10.minutes, 82)
        )
        val dist = listOf(
            DistanceEntry(timeStart, timeStart + 10.minutes, Length(0.0)),
            DistanceEntry(timeStart + 10.minutes, timeStart + 20.minutes, Length(50.0))
        )
        val raw = Predictor.MultiTimeSeriesEntries(timeStart, duration, hr, dist)

        val result = FallbackHandler.ensureData(raw)

        assertEquals(hr, result.heartRate)
        assertEquals(dist, result.distance)
    }
}
