package org.htwk.pacing.backend.predictor

import org.junit.Test
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import org.junit.Assert.assertArrayEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import junit.framework.TestCase.assertTrue
import kotlin.test.assertEquals

class PreprocessorTest {

    private val now = Clock.System.now()

    @Test
    fun `valid heart rates are kept`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = listOf(
                HeartRateEntry(now, 80),
                HeartRateEntry(now + 1.minutes, 100)
            ),
            distance = emptyList()
        )

        val result = Preprocessor.run(raw)

        val expectedHeartRates = floatArrayOf(80f, 100f)
        assertArrayEquals(expectedHeartRates, result.heartRate, 0.001f)
        assertEquals(0.0, result.cleanedHeartRatesRatio, 0.001)
    }

    @Test
    fun `invalid heart rates are replaced`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = listOf(
                HeartRateEntry(now, 20),   // <30 invalid
                HeartRateEntry(now + 1.minutes, 250) // >220 invalid
            ),
            distance = emptyList()
        )

        val result = Preprocessor.run(raw)

        val expectedHeartRates = floatArrayOf(0f, 0f)
        assertArrayEquals(expectedHeartRates, result.heartRate, 0.001f)
        assertEquals(1.0, result.cleanedHeartRatesRatio, 0.001)
    }

    @Test
    fun `duplicate heart rate entries are removed`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = listOf(
                HeartRateEntry(now, 80),
                HeartRateEntry(now, 150) // duplicate, will be removed
            ),
            distance = emptyList()
        )

        val result = Preprocessor.run(raw)

        val expectedHeartRates = floatArrayOf(80f)
        assertArrayEquals(expectedHeartRates, result.heartRate, 0.001f)
        assertEquals(0.0, result.cleanedHeartRatesRatio, 0.001)
    }

    @Test
    fun `valid distances are kept`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(50.0)),
                DistanceEntry(now + 5.minutes, now + 10.minutes, Length(0.0)) // edge case valid
            )
        )

        val result = Preprocessor.run(raw)

        val expectedDistances = floatArrayOf(50f, 0f)
        assertArrayEquals(expectedDistances, result.distance, 0.001f)
        assertEquals(0.0, result.cleanedDistancesRatio, 0.001)
    }

    @Test
    fun `invalid distances are replaced`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(-10.0)), // invalid
                DistanceEntry(now + 5.minutes, now + 10.minutes, Length(-5.0)) // invalid
            )
        )

        val result = Preprocessor.run(raw)

        val expectedDistances = floatArrayOf(0f, 0f)
        assertArrayEquals(expectedDistances, result.distance, 0.001f)
        assertEquals(1.0, result.cleanedDistancesRatio, 0.001)
    }

    @Test
    fun `duplicate distance entries are removed`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(75.0)),
                DistanceEntry(now, now + 5.minutes, Length(75.0)) // duplicate
            )
        )

        val result = Preprocessor.run(raw)

        val expectedDistances = floatArrayOf(75f)
        assertArrayEquals(expectedDistances, result.distance, 0.001f)
        assertEquals(0.0, result.cleanedDistancesRatio, 0.001)
    }

    @Test
    fun `timeStart is roughly 6 hours before now`() {
        val raw = Predictor.MultiTimeSeriesList(
            heartRate = emptyList(),
            distance = emptyList()
        )

        val result = Preprocessor.run(raw)
        val expectedStart = now - 6.hours
        val toleranceSeconds = 10
        val diff = kotlin.math.abs(result.timeStart.epochSeconds - expectedStart.epochSeconds)
        assertTrue("timeStart should be roughly 6 hours before now", diff < toleranceSeconds)
    }
}