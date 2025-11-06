package org.htwk.pacing.backend.predictor.preprocessing

//unit tests for preprocessor.run

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PreprocessorTests {
    companion object {
        private val timeStart = Instant.fromEpochMilliseconds(0);

        private val heartRateData = listOf(
            //the first two values should fall into first bucket, averaging to 70 bpm in bucket
            HeartRateEntry(time = timeStart, bpm = 65),
            HeartRateEntry(time = timeStart + 5.seconds, bpm = 75),

            //second value in later bucket, no values in between -> expect linear interpolation
            HeartRateEntry(
                time = timeStart + Predictor.TIME_SERIES_STEP_DURATION * 10 + 5.seconds,
                bpm = 80
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

    @Test
    fun `run processes heart rate data correctly`() {
        val rawData = Predictor.MultiTimeSeriesEntries(
            timeStart = timeStart,
            heartRate = heartRateData,
            distance = distanceData
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0);

        val result = Preprocessor.run(rawData, fixedParameters)

        assertEquals(timeStart, result.timeStart)

        // The placeholder implementation of discretizeTimeSeries fills the array with the first value.
        // The size should be TIME_SERIES_DURATION / 10.minutes
        val expectedSize = (TIME_SERIES_DURATION.inWholeMinutes / 10).toInt()
        assertEquals(expectedSize, result.heartRate.proportional.size)

        val expectedResultHeartRate = doubleArrayOf(
            70.0,
            71.0,
            72.0,
            73.0,
            74.0,
            75.0,
            76.0,
            77.0,
            78.0,
            79.0,
            80.0,
            80.0,
            80.0
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

    /*@Test
    fun `Preprocessor test run with distanceEntries`() {

    }*/

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