package org.htwk.pacing.backend.predictor.preprocessing

import junit.framework.TestCase.assertEquals
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
import org.htwk.pacing.backend.database.Velocity
import org.htwk.pacing.backend.predictor.Predictor
import org.junit.Test
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BuildMetricTimeSeriesTest {
    private val timeStart = Instant.fromEpochMilliseconds(0)
    private val duration = 60.minutes

    private val threshold = 110.0

    fun buildExampleRaw(metric: TimeSeriesMetric): Predictor.MultiTimeSeriesEntries {

        return Predictor.MultiTimeSeriesEntries(
            timeStart = timeStart,
            duration = duration,
            heartRate = if (metric == TimeSeriesMetric.HEART_RATE || metric == TimeSeriesMetric.HEART_RATE_VARIABILITY)
                listOf(
                    HeartRateEntry(timeStart + 0.seconds, 80),
                    HeartRateEntry(timeStart + 10.seconds, 120),
                    HeartRateEntry(timeStart + 20.seconds, 150)
                )
            else emptyList(),

            heartRateVariability = if (metric == TimeSeriesMetric.HEART_RATE_VARIABILITY)
                listOf(
                    HeartRateVariabilityEntry(timeStart + 0.seconds, 45.0),
                    HeartRateVariabilityEntry(timeStart + 10.seconds, 25.0),
                    HeartRateVariabilityEntry(timeStart + 20.seconds, 37.0),
                )
            else emptyList(),

            distance = if (metric == TimeSeriesMetric.DISTANCE)
                listOf(
                    DistanceEntry(
                        timeStart + 0.seconds,
                        timeStart + 10.seconds,
                        Length.meters(50.0)
                    ),
                )
            else emptyList(),

            elevationGained = if (metric == TimeSeriesMetric.ELEVATION_GAINED)
                listOf(
                    ElevationGainedEntry(
                        timeStart + 0.seconds,
                        timeStart + 10.seconds,
                        Length.meters(50.0)
                    ),
                )
            else emptyList(),

            skinTemperature = if (metric == TimeSeriesMetric.SKIN_TEMPERATURE)
                listOf(
                    SkinTemperatureEntry(timeStart + 0.seconds, Temperature.celsius(35.0)),
                )
            else emptyList(),

            oxygenSaturation = if (metric == TimeSeriesMetric.OXYGEN_SATURATION)
                listOf(
                    OxygenSaturationEntry(timeStart + 0.seconds, Percentage.fromDouble(70.0)),
                )
            else emptyList(),

            steps = if (metric == TimeSeriesMetric.STEPS)
                listOf(
                    StepsEntry(timeStart + 0.seconds, timeStart + 10.seconds, 30),
                )
            else emptyList(),

            speed = if (metric == TimeSeriesMetric.SPEED)
                listOf(
                    SpeedEntry(timeStart + 0.seconds, Velocity.metersPerSecond(2.0)),
                )
            else emptyList(),

            sleepSession = if (metric == TimeSeriesMetric.SLEEP_SESSION)
                listOf(
                    SleepSessionEntry(
                        timeStart + 0.seconds, timeStart + 10.seconds, SleepStage.Sleeping
                    )
                )
            else emptyList()
        )
    }

    // Oxygen Saturation uses Log, can't be checked here
    val metricsToTest = listOf(
        TimeSeriesMetric.HEART_RATE,
        TimeSeriesMetric.HEART_RATE_VARIABILITY,
        TimeSeriesMetric.DISTANCE,
        TimeSeriesMetric.ELEVATION_GAINED,
        TimeSeriesMetric.SKIN_TEMPERATURE,
        TimeSeriesMetric.STEPS,
        TimeSeriesMetric.SPEED,
        TimeSeriesMetric.SLEEP_SESSION
    )

    @Test
    fun `buildMetricTimeSeries produces expected results for all metrics`() {
        metricsToTest.forEach { metric ->
            val raw = buildExampleRaw(metric)
            val result = buildMetricTimeSeries(metric, raw, Predictor.FixedParameters(110.0))

            println("Testing metric: $metric")

            val expectedSize = when (metric) {
                TimeSeriesMetric.HEART_RATE, TimeSeriesMetric.HEART_RATE_VARIABILITY -> raw.heartRate.size
                TimeSeriesMetric.DISTANCE -> raw.distance.size
                TimeSeriesMetric.ELEVATION_GAINED -> raw.elevationGained.size
                TimeSeriesMetric.SKIN_TEMPERATURE -> raw.skinTemperature.size
                TimeSeriesMetric.OXYGEN_SATURATION -> raw.oxygenSaturation.size
                TimeSeriesMetric.STEPS -> raw.steps.size
                TimeSeriesMetric.SPEED -> raw.speed.size
                TimeSeriesMetric.SLEEP_SESSION -> raw.sleepSession.size
            }

            assertEquals(expectedSize, result.data.size)

            result.data.forEachIndexed { index, dp ->
                val expectedValue = when (metric) {
                    TimeSeriesMetric.HEART_RATE -> {
                        val hr = raw.heartRate[index].bpm
                        val overload = ((hr - threshold) / threshold).coerceAtLeast(0.0)
                        val intensityFactor = (1.0 + 5 * overload.pow(1.5)).coerceAtMost(1.6)
                        hr * intensityFactor
                    }

                    TimeSeriesMetric.HEART_RATE_VARIABILITY -> {
                        val hrAtTime = raw.heartRate
                            .lastOrNull { it.time <= raw.heartRateVariability[index].time }
                            ?.bpm
                        val overload = if (hrAtTime != null) {
                            (hrAtTime - threshold).coerceAtLeast(0.0)
                        } else {
                            0.0
                        }
                        val factor = (1.0 - overload / 100.0).coerceIn(0.5, 1.0)
                        raw.heartRateVariability[index].variability * factor
                    }
                    else -> {
                        dp.value
                    }
                }

                assertEquals(expectedValue, dp.value)

                println("Index $index, expected=$expectedValue, actual=${dp.value}")

            }
        }
    }
}


