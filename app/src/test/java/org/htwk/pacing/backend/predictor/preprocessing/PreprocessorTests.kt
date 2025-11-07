package org.htwk.pacing.backend.predictor.preprocessing

//unit tests for preprocessor.run

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
import kotlin.time.Duration.Companion.seconds

class PreprocessorTests {
    companion object {
        private val timeStart = Instant.fromEpochMilliseconds(0);

        private val heartRateData = listOf(
            //the first two values should fall into first bucket, averaging to 70 bpm in bucket
            HeartRateEntry(time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 5, bpm = 70),
            HeartRateEntry(
                time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 5 + 5.seconds,
                bpm = 80
            ),

            //second value in later bucket, no values in between -> expect linear interpolation
            HeartRateEntry(
                time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 10 + 5.seconds,
                bpm = 80
            ),
            HeartRateEntry(
                time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 11 + 5.seconds,
                bpm = 100
            ),
            HeartRateEntry(
                time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 16 + 5.seconds,
                bpm = 50
            )
        )

        private val distanceData = listOf(
            //the first two values should fall into second bucket, summing to 800 meters in bucket
            DistanceEntry(
                start = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 1 + 0.seconds,
                end = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 1 + 30.seconds,
                length = Length(500.0)
            ),
            DistanceEntry(
                start = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 1 + 1.minutes,
                end = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 1 + 2.minutes,
                length = Length(300.0)
            ),

            //falls in later bucket, expect no linear interpolation since steps are aggregated

            DistanceEntry(
                start = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 5 + 5.seconds,
                end = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 5 + 90.seconds,
                length = Length(150.0)
            )
        )
    }

    private val now = Clock.System.now()

    // --- tests for input data cleansing ---

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

    // --- generic preprocessor tests ---

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
            distance = distanceData
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)

        val result = Preprocessor.run(rawData, fixedParameters)

        assertEquals(timeStart, result.timeStart)

        // The placeholder implementation of discretizeTimeSeries fills the array with the first value.
        // The size should be TIME_SERIES_DURATION / 10.minutes
        val expectedSize = (TIME_SERIES_DURATION.inWholeMinutes / 10).toInt()
        assertEquals(expectedSize, result.heartRate.proportional.size)

        val expectedResultHeartRate = doubleArrayOf(
            75.0,
            75.0,
            75.0,
            75.0,
            75.0,
            75.0,
            76.0,
            77.0,
            78.0,
            79.0,
            80.0,
            100.0,
            90.0,
            80.0,
            70.0,
            60.0,
            50.0,
            50.0,
            50.0
        )

        for (i in 0 until expectedResultHeartRate.size) {
            assertTrue(expectedResultHeartRate[i] == result.heartRate.proportional[i]);
        }

        //expected accumulated (running sum, since we're doing an integral for the distance)
        //TODO: add expectation of accumulated sum as soon as we add discrete integration
        val expectedResultDistance = doubleArrayOf(
            0.0, //no value yet
            /*800.0, //first distance entries
            800.0,
            800.0,
            800.0,
            950.0,
            950.0*/
        )

        for (i in 0 until expectedResultDistance.size) {
            //assertTrue(expectedResultDistance[i] == result.distance.integral[i]);
        }
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
            distance = emptyList()
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)


        var exceptionThrown = false
        try {
            Preprocessor.run(rawData, fixedParameters)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
    }
}