package org.htwk.pacing.backend.predictor.preprocessing

//unit tests for preprocessor.run

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class PreprocessorTests {
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
            heartRate = heartRateData
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThreshold = 80.0);

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
            heartRate = heartRateData
        )

        val fixedParameters = Predictor.FixedParameters(anaerobicThreshold = 80.0);


        var exceptionThrown: Boolean = false;
        try {
            Preprocessor.run(rawData, fixedParameters)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);
    }
}