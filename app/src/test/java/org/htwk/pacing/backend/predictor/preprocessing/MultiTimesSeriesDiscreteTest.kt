package org.htwk.pacing.backend.predictor.preprocessing

import junit.framework.TestCase.assertEquals
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.*
import org.htwk.pacing.backend.predictor.Predictor
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*class MultiTimesSeriesDiscreteTest {
    private val timeStart = Instant.fromEpochMilliseconds(0)
    private val duration = 60.minutes

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
                        timeStart + 0.seconds, timeStart + 10.seconds, SleepStage.Awake
                    )
                )
            else emptyList()
        )
    }

    // Oxygen Saturation and Sleep couldn't be checked
    val metricsToTest = listOf(
        TimeSeriesMetric.HEART_RATE,
        TimeSeriesMetric.HEART_RATE_VARIABILITY,
        TimeSeriesMetric.DISTANCE,
        TimeSeriesMetric.ELEVATION_GAINED,
        TimeSeriesMetric.SKIN_TEMPERATURE,
        TimeSeriesMetric.STEPS,
        TimeSeriesMetric.SPEED
    )

    @Test
    fun `buildGenericTimeSeries produces expected results for all metrics`() {
        metricsToTest.forEach { metric ->
            val raw = buildExampleRaw(metric)
            val result = buildGenericTimeSeries(metric, raw)

            println("Testing metric: $metric")

            assertEquals(raw.timeStart, result.timeStart)
            assertEquals(raw.duration, result.duration)

            assertEquals(metric.signalClass == TimeSeriesSignalClass.CONTINUOUS, result.isContinuous)

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
                        TimeSeriesMetric.HEART_RATE -> raw.heartRate[index].bpm.toDouble()
                        TimeSeriesMetric.HEART_RATE_VARIABILITY -> raw.heartRateVariability[index].variability
                        TimeSeriesMetric.DISTANCE -> raw.distance[index].length.inMeters()
                        TimeSeriesMetric.ELEVATION_GAINED -> raw.elevationGained[index].length.inMeters()
                        TimeSeriesMetric.SKIN_TEMPERATURE -> raw.skinTemperature[index].temperature.inCelsius()
                        TimeSeriesMetric.OXYGEN_SATURATION -> raw.oxygenSaturation[index].percentage.toDouble()
                        TimeSeriesMetric.STEPS -> raw.steps[index].count.toDouble()
                        TimeSeriesMetric.SPEED -> raw.speed[index].velocity.inKilometersPerHour()
                        else -> throw IllegalStateException("Untreated metric: $metric")
                    }
                    assertEquals(expectedValue, dp.value)
                    println("Index $index, expected=$expectedValue, actual=${dp.value}")
                }
            }
        }
    }
*/
