package org.htwk.pacing.backend.predictor.preprocessing

//unit tests for preprocessor.run

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PreprocessorTests {

    private val now = Clock.System.now()

    // --- Tests zur Datenbereinigung ---

    @Test
    fun validHeartRatesAreKept() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = now - 6.hours,
            heartRate = listOf(
                HeartRateEntry(now, 80),
                HeartRateEntry(now + 1.minutes, 100)
            ),
            distance = emptyList()
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedHeartRates = floatArrayOf(80f, 100f)
        assertArrayEquals(
            expectedHeartRates,
            results.heartRate.map { it.bpm.toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(1.0, ratios.cleanedHeartRatesRatio.toDouble(), 0.001)
    }

    @Test
    fun invalidHeartRatesAreDeleted() {
        val raw = Predictor.MultiTimeSeriesEntries(
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
        assertEquals(0.5, ratios.cleanedHeartRatesRatio.toDouble(), 0.001)
    }

    @Test
    fun invalidAndDuplicateHeartRatesAreDeleted() {
        val raw = Predictor.MultiTimeSeriesEntries(
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
        assertEquals(0.25, ratios.cleanedHeartRatesRatio.toDouble(), 0.001)
    }

    @Test
    fun duplicateHeartRateEntriesAreRemoved() {
        val raw = Predictor.MultiTimeSeriesEntries(
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
        assertEquals(0.5, ratios.cleanedHeartRatesRatio.toDouble(), 0.001)
    }

    @Test
    fun validDistancesAreKept() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = listOf(
                DistanceEntry(now, now + 5.minutes, Length(50.0)),
                DistanceEntry(now + 5.minutes, now + 10.minutes, Length(0.0)) // edge case valid
            )
        )

        val (results, ratios) = cleanInputData(raw)

        val expectedDistances = floatArrayOf(50f)
        assertArrayEquals(
            expectedDistances,
            results.distance.map { it.length.inMeters().toFloat() }.toFloatArray(),
            0.001f
        )
        assertEquals(0.5, ratios.cleanedDistancesRatio.toDouble(), 0.001)
    }

    @Test
    fun invalidDistancesAreDeleted() {
        val raw = Predictor.MultiTimeSeriesEntries(
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
        assertEquals(0.0, ratios.cleanedDistancesRatio.toDouble(), 0.001)
    }

    @Test
    fun duplicateDistanceEntriesAreRemoved() {
        val raw = Predictor.MultiTimeSeriesEntries(
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
        assertEquals(0.5, ratios.cleanedDistancesRatio.toDouble(), 0.001)
    }

    @Test
    fun timeStartIsRoughly6HoursBeforeNow() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = now - 6.hours,
            heartRate = emptyList(),
            distance = emptyList()
        )

        val (results, _) = cleanInputData(raw)
        val expectedStart = now - 6.hours
        val toleranceSeconds = 0
        val diff = abs(results.timeStart.epochSeconds - expectedStart.epochSeconds)
        assertTrue("timeStart should be roughly 6 hours before now", diff <= toleranceSeconds)
    }

    // --- Tests zum Preprocessor.run Verhalten ---

    @Test
    fun `run processes heart rate data correctly`() {
        val now = Clock.System.now()
        val timeStart = now - TIME_SERIES_DURATION

        val heartRateData = listOf(
            HeartRateEntry(time = timeStart, bpm = 70),
            HeartRateEntry(time = timeStart + 1.minutes, bpm = 75)
        )

        val rawData = Predictor.MultiTimeSeriesEntries(
            timeStart = timeStart,
            heartRate = heartRateData,
            distance = listOf(DistanceEntry(start = timeStart, end = timeStart + 1.minutes, length = Length(100.0)
            ))
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0);

        val result = Preprocessor.run(rawData, fixedParameters)

        assertEquals(timeStart, result.timeStart)

        // The placeholder implementation of discretizeTimeSeries fills the array with the first value.
        // The size should be TIME_SERIES_DURATION / 10.minutes
        val expectedSize = (TIME_SERIES_DURATION.inWholeMinutes / 10).toInt()
        assertEquals(expectedSize, result.heartRate.proportional.size)

        // Check if all values in the proportional array are the first BPM value (70.0)
        assertTrue(result.heartRate.proportional.all { it == 70.0 })
    }

    @Test
    fun `Preprocessor run needs to throw exception of input data is empty`() {
        val now = Clock.System.now()
        val timeStart = now - TIME_SERIES_DURATION

        // Test with one entry, which is not enough for the placeholder `discretizeTimeSeries`
        val heartRateData: List<HeartRateEntry> = listOf()

        val rawData = Predictor.MultiTimeSeriesEntries(
            timeStart = timeStart,
            heartRate = heartRateData,
            distance = listOf()
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0);


        var exceptionThrown: Boolean = false;
        try {
            Preprocessor.run(rawData, fixedParameters)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);
    }
}