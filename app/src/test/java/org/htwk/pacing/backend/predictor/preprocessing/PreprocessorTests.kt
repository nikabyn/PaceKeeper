package org.htwk.pacing.backend.predictor.preprocessing

//unit tests for preprocessor.run

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PreprocessorTests {
    companion object {
        private val timeStart = Instant.fromEpochMilliseconds(0)

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

    // --- generic preprocessor tests ---

    @Test
    fun `run processes heart rate data correctly`() {
        val rawData = MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = timeStart,
            heartRate = heartRateData,
            distance = distanceData
        )

        val fixedParameters = FixedParameters(anaerobicThresholdBPM = 80.0)

        val result = Preprocessor.run(rawData, fixedParameters)

        assertEquals(timeStart, result.timeStart)

        // The placeholder implementation of discretizeTimeSeries fills the array with the first value.
        // The size should be TIME_SERIES_DURATION / 10.minutes
        val expectedLength = (Predictor.TIME_SERIES_DURATION.inWholeMinutes / 10).toInt()

        assertEquals(
            expectedLength,
            result.stepCount()
        )

        val expectedDiscreteHeartRate = doubleArrayOf(
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
            160.0,
            138.0,
            116.0,
            94.0,
            72.0,
            50.0,
            50.0,
            50.0
        )

        for (i in 0 until expectedDiscreteHeartRate.size) {
            assertTrue(
                expectedDiscreteHeartRate[i] == result.get(
                    MultiTimeSeriesDiscrete.FeatureID(
                        TimeSeriesMetric.HEART_RATE,
                        PIDComponent.PROPORTIONAL
                    ), i
                )
            );
        };

        //expected accumulated (running sum, since we're doing an integral for the distance)
        //TODO: add expectation of accumulated sum as soon as we add discrete integration
        val expectedResultDistance = doubleArrayOf(
            0.0, //no value yet
            400.0, //first distance entries (1/2 of 800.0 here because we're doing trapezoidal integration)
            800.0,
            800.0,
            800.0,
            875.0,
            950.0,
            950.0,
            950.0
        )

            /*for (i in 0 until expectedResultDistance.size) {
            assertTrue(
                expectedResultDistance[i] == result.getSampleOfFeature(
                    MultiTimeSeriesDiscrete.FeatureID(
                        TimeSeriesMetric.DISTANCE,
                        PIDComponent.INTEGRAL
                    ), i
                )
            );
        }*/
        }

        @Test
        fun `Preprocessor run needs accept case where timeStart = first entry time`() {
            val rawData = MultiTimeSeriesEntries.createDefaultEmpty(
                timeStart = timeStart,
                heartRate = listOf(HeartRateEntry(time = timeStart, bpm = 60)),
                distance = listOf(
                    DistanceEntry(
                        start = timeStart,
                        end = timeStart,
                        length = Length(100.0)
                    )
                )
            )

            val fixedParameters = FixedParameters(anaerobicThresholdBPM = 80.0)

            val result = Preprocessor.run(rawData, fixedParameters)

            assertEquals(timeStart, result.timeStart)
        }

        @Test
        fun `Preprocessor run needs to not throw exception of input data is empty`() {
            // Test with one entry, which is not enough for the placeholder `discretizeTimeSeries`
            val heartRateData: List<HeartRateEntry> = listOf()

            val rawData = MultiTimeSeriesEntries.createDefaultEmpty(
                timeStart = timeStart,
                heartRate = heartRateData,
                distance = emptyList()
            )

            val fixedParameters = FixedParameters(anaerobicThresholdBPM = 80.0)

            val result = Preprocessor.run(rawData, fixedParameters)
        }
    }