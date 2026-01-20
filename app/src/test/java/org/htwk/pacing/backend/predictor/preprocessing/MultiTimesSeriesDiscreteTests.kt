package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.predictor.Predictor
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiTimesSeriesDiscreteTests {

    private val start = Instant.parse("2024-01-01T00:00:00Z")
    private val end = Instant.parse("2024-01-01T00:00:30Z")
    private val step = Predictor.TIME_SERIES_STEP_DURATION

    private fun fixedParams(threshold: Double) =
        Predictor.FixedParameters(anaerobicThresholdBPM = threshold)

    @Test
    fun `heart rate below threshold is not amplified`() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = start,
            duration = end - start,
            heartRate = listOf(
                HeartRateEntry(start, 120L)
            ),
            heartRateVariability = listOf(
                HeartRateVariabilityEntry(start, 0.5)
            ),
            distance = listOf(
                DistanceEntry(start, end, Length(0.5))
            ),
            elevationGained = listOf(
                ElevationGainedEntry(start, end, Length(0.5))
            ),
            skinTemperature = listOf(
                SkinTemperatureEntry(start, Temperature.celsius(37.0))
            ),
            oxygenSaturation = listOf(
                OxygenSaturationEntry(start, Percentage.fromDouble(0.6))
            ),
            steps = listOf(
                StepsEntry(start, end, 2L)
            ),
            speed = listOf(
                SpeedEntry(start, org.htwk.pacing.backend.database.Velocity(30.0))
            ),
            sleepSession = listOf(
                SleepSessionEntry(start, end, SleepStage.Awake)
            )
        )

        val mts = MultiTimeSeriesDiscrete.fromEntries(raw, fixedParams(140.0))

        val hr = mts[
            MultiTimeSeriesDiscrete.FeatureID(
                TimeSeriesMetric.HEART_RATE,
                PIDComponent.PROPORTIONAL
            ),
            0
        ]

        assertEquals(120.0, hr, 1e-6)
    }

    @Test
    fun `heart rate above threshold is amplified`() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = start,
            duration = step,
            heartRate = listOf(
                HeartRateEntry(start, 160L)
            ),
            heartRateVariability = emptyList(),
            distance = emptyList(),
            elevationGained = emptyList(),
            skinTemperature = emptyList(),
            oxygenSaturation = emptyList(),
            steps = emptyList(),
            speed = emptyList(),
            sleepSession = emptyList()
        )

        val mts = MultiTimeSeriesDiscrete.fromEntries(raw, fixedParams(140.0))

        val expected = 160.0 * 1.4
        val hr = mts[
            MultiTimeSeriesDiscrete.FeatureID(
                TimeSeriesMetric.HEART_RATE,
                PIDComponent.PROPORTIONAL
            ),
            0
        ]

        assertEquals(expected, hr, 1e-6)
    }

    @Test
    fun `heart rate amplification is capped at 1_5`() {
        val raw = Predictor.MultiTimeSeriesEntries(
            timeStart = start,
            duration = step,
            heartRate = listOf(
                HeartRateEntry(start, 220L)
            ),
            heartRateVariability = emptyList(),
            distance = emptyList(),
            elevationGained = emptyList(),
            skinTemperature = emptyList(),
            oxygenSaturation = emptyList(),
            steps = emptyList(),
            speed = emptyList(),
            sleepSession = emptyList()
        )

        val mts = MultiTimeSeriesDiscrete.fromEntries(raw, fixedParams(140.0))

        val hr = mts[
            MultiTimeSeriesDiscrete.FeatureID(
                TimeSeriesMetric.HEART_RATE,
                PIDComponent.PROPORTIONAL
            ),
            0
        ]

        assertEquals(220.0 * 1.5, hr, 1e-6)
    }
}