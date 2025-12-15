package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class CleanInputDataTests {
    private val now = Instant.parse("2025-05-05T12:00:00Z")

    // --- tests for input data cleansing ---

    @Test
    fun validHeartRatesAreKept() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = listOf(
                HeartRateEntry(now, 80),
                HeartRateEntry(now + 1.minutes, 100)
            ),
            distance = emptyList(),
            elevationGained = emptyList(),
            skinTemperature = emptyList(),
            heartRateVariability = emptyList(),
            oxygenSaturation = emptyList(),
            steps = emptyList(),
            speed = emptyList(),
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedHeartRates = floatArrayOf(80f, 100f)
        assertArrayEquals(
            expectedHeartRates,
            results.heartRate.map { it.bpm.toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(1.0, ratios.ratiosPerMetric[TimeSeriesMetric.HEART_RATE]!!.toDouble(), 0.001)
    }

    @Test
    fun invalidHeartRatesAreDeleted() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = listOf(
                HeartRateEntry(now, 20),   // <30 invalid
                HeartRateEntry(now + 1.minutes, 250), // >220 invalid
                HeartRateEntry(now + 2.minutes, 70), // keep
                HeartRateEntry(now + 3.minutes, 70) // keep

            ),
            distance = emptyList()
        )
        val (results, ratios) = cleanInputData(raw)

        val expectedHeartRates = floatArrayOf(70f, 70f)
        assertArrayEquals(
            expectedHeartRates,
            results.heartRate.map { it.bpm.toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(0.5, ratios.ratiosPerMetric[TimeSeriesMetric.HEART_RATE]!!.toDouble(), 0.001)
    }

    @Test
    fun invalidAndDuplicateHeartRatesAreDeleted() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = listOf(
                HeartRateEntry(now, 20),              // <30 invalid
                HeartRateEntry(now + 1.minutes, 250), // >220 invalid
                HeartRateEntry(now + 1.minutes, 70), // duplicate time -> deleted
                HeartRateEntry(now + 2.minutes, 70) // keep
            ),
            distance = emptyList()
        )
        val (results, ratios) = cleanInputData(raw)

        val expectedHeartRates = floatArrayOf(70f)
        assertArrayEquals(
            expectedHeartRates,
            results.heartRate.map { it.bpm.toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(0.25, ratios.ratiosPerMetric[TimeSeriesMetric.HEART_RATE]!!.toDouble(), 0.001)
    }

    @Test
    fun duplicateHeartRateEntriesAreRemoved() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = listOf(
                HeartRateEntry(now, 80),
                HeartRateEntry(now, 150) // duplicate, will be removed
            ),
            distance = emptyList()
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedHeartRates = floatArrayOf(80f)
        assertArrayEquals(
            expectedHeartRates,
            results.heartRate.map { it.bpm.toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(0.5, ratios.ratiosPerMetric[TimeSeriesMetric.HEART_RATE]!!.toDouble(), 0.001)
    }

    @Test
    fun validDistancesAreKept() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(50.0)),
                DistanceEntry(now + 5.minutes, now + 10.minutes, Length(0.0)) // edge case
            )
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedDistances = floatArrayOf(50f, 0.0f)
        assertArrayEquals(
            expectedDistances,
            results.distance.map { it.length.inMeters().toFloat() }.toFloatArray(),
            0.001f
        )
        //assertEquals(0.5, ratios.ratiosPerMetric[TimeSeriesMetric.DISTANCE]!!.toDouble(), 0.001)
    }

    @Test
    fun invalidDistancesAreDeleted() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(-10.0)), // invalid
                DistanceEntry(now + 5.minutes, now + 10.minutes, Length(-5.0)) // invalid
            )
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedDistances = floatArrayOf()
        assertArrayEquals(
            expectedDistances,
            results.distance.map { it.length.inMeters().toFloat() }.toFloatArray(),
            0.001f
        )
        //assertEquals(0.0, ratios.ratiosPerMetric[TimeSeriesMetric.DISTANCE]!!.toDouble(), 0.001)
    }

    @Test
    fun duplicateDistanceEntriesAreRemoved() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(75.0)),
                DistanceEntry(now, now + 5.minutes, Length(75.0)) // duplicate
            )
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedDistances = floatArrayOf(75f)
        assertArrayEquals(
            expectedDistances,
            results.distance.map { it.length.inMeters().toFloat() }.toFloatArray(),
            0.001f
        )
        //assertEquals(0.5, ratios.ratiosPerMetric[TimeSeriesMetric.DISTANCE]!!.toDouble(), 0.001)
    }

    @Test
    fun timeStartIsRoughly6HoursBeforeNow() {
        val raw = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = emptyList(),
        )

        val (results, _) = cleanInputData(raw)
        val expectedStart = now - 6.hours
        val toleranceSeconds = 0
        val diff = abs(results.timeStart.epochSeconds - expectedStart.epochSeconds)
        assertTrue("timeStart should be roughly 6 hours before now", diff <= toleranceSeconds)
    }
}