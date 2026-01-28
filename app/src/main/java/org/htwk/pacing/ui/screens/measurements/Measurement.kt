package org.htwk.pacing.ui.screens.measurements

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastMap
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.database.MenstruationPeriodEntry
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.ui.components.Graph
import org.htwk.pacing.ui.components.graphToPaths
import org.htwk.pacing.ui.screens.measurements.Measurement.Distance
import org.htwk.pacing.ui.screens.measurements.Measurement.ElevationGained
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRate
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRateVariabilityRmssd
import org.htwk.pacing.ui.screens.measurements.Measurement.MenstruationPeriod
import org.htwk.pacing.ui.screens.measurements.Measurement.OxygenSaturation
import org.htwk.pacing.ui.screens.measurements.Measurement.SkinTemperature
import org.htwk.pacing.ui.screens.measurements.Measurement.Sleep
import org.htwk.pacing.ui.screens.measurements.Measurement.Speed
import org.htwk.pacing.ui.screens.measurements.Measurement.Steps
import org.htwk.pacing.ui.screens.measurements.Measurement.Symptoms
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

enum class Measurement {
    Steps,
    Distance,
    ElevationGained,
    Speed,
    HeartRate,
    MenstruationPeriod,
    OxygenSaturation,
    Sleep,
    HeartRateVariabilityRmssd,
    SkinTemperature,
    Symptoms;

    @Composable
    fun title() = when (this) {
        Steps -> stringResource(R.string.steps)
        Distance -> stringResource(R.string.distance)
        ElevationGained -> stringResource(R.string.elevation_gained)
        Speed -> stringResource(R.string.speed)
        HeartRate -> stringResource(R.string.heart_rate)
        MenstruationPeriod -> stringResource(R.string.menstruation)
        OxygenSaturation -> stringResource(R.string.oxygen_saturation)
        Sleep -> stringResource(R.string.sleep)
        HeartRateVariabilityRmssd -> stringResource(R.string.heart_rate_variability)
        SkinTemperature -> stringResource(R.string.skin_temperature_variation)
        Symptoms -> stringResource(R.string.symptoms)
    }

    fun dao(db: PacingDatabase) = when (this) {
        Steps -> db.stepsDao()
        Distance -> db.distanceDao()
        ElevationGained -> db.elevationGainedDao()
        Speed -> db.speedDao()
        HeartRate -> db.heartRateDao()
        MenstruationPeriod -> db.menstruationPeriodDao()
        OxygenSaturation -> db.oxygenSaturationDao()
        Sleep -> db.sleepSessionsDao()
        HeartRateVariabilityRmssd -> db.heartRateVariabilityDao()
        SkinTemperature -> db.skinTemperatureDao()
        Symptoms -> db.manualSymptomDao()
    }

    fun stepSize() = when (this) {
        Steps -> 500.0
        Distance -> 2.0
        ElevationGained -> 50.0
        Speed -> 2.0
        HeartRate -> 50.0
        SkinTemperature -> 0.25

        OxygenSaturation,
        HeartRateVariabilityRmssd -> 20.0

        Symptoms, MenstruationPeriod, Sleep -> null
    }

    fun yRange(yData: List<Double>): ClosedRange<Double> {
        fun Double.roundAwayFromZero(stepSize: Double?): Double {
            if (stepSize == null || stepSize.isNaN() || stepSize.isInfinite()) return this

            val scaled = this / stepSize
            val rounded = kotlin.math.ceil(scaled.absoluteValue) * scaled.sign
            return rounded * stepSize
        }

        val yMin = if (yData.isEmpty()) null else yData.min()
        val yMax = if (yData.isEmpty()) null else yData.max()
        val stepSize = stepSize()
        val roundedYMin = yMin?.roundAwayFromZero(stepSize)
        val roundedYMax = yMax?.roundAwayFromZero(stepSize)

        return when (this) {
            Steps -> 0.0..(roundedYMax ?: 1_000.0)
            Distance -> 0.0..(roundedYMax ?: 6.0)
            ElevationGained -> 0.0..(roundedYMax ?: 100.0)
            Speed -> 0.0..(roundedYMax ?: 6.0)
            HeartRate -> 0.0..(roundedYMax ?: 150.0)
            SkinTemperature -> (roundedYMin ?: -0.5)..(roundedYMax ?: 0.5)

            OxygenSaturation, HeartRateVariabilityRmssd -> 0.0..100.0

            MenstruationPeriod -> 0.0..24.0
            Sleep -> 0.0..3.0
            Symptoms -> 0.0..3.0
        }
    }

    @Composable
    fun ySteps(yRange: ClosedRange<Double>): List<String> {
        val stepSize = stepSize() ?: 0.0
        val ySteps = when (this) {
            Steps,
            Distance,
            ElevationGained,
            Speed,
            HeartRate,
            SkinTemperature,
            OxygenSaturation,
            HeartRateVariabilityRmssd -> {
                val start = yRange.start
                val end = yRange.endInclusive

                val stepCount =
                    ((end - start) / stepSize).roundToInt().coerceAtLeast(0)

                (0..stepCount).map { i ->
                    val value = start + i * stepSize
                    if (stepSize < 1.0)
                        "%.2f".format(value)
                    else
                        value.toInt().toString()
                }
            }

            MenstruationPeriod -> emptyList()

            Sleep -> listOf("Deep", "Light", "REM", "Awake")

            Symptoms -> listOf(
                stringResource(R.string.very_bad),
                stringResource(R.string.bad),
                stringResource(R.string.good),
                stringResource(R.string.very_good),
            )
        }

        return ySteps
    }

    fun entryToXValue(entry: TimedEntry) = entry.end.toEpochMilliseconds().toDouble()

    fun entryToYValue(entry: TimedEntry) = when (this) {
        Steps -> (entry as StepsEntry).count.toDouble()
        Distance -> (entry as DistanceEntry).length.inKilometers()
        ElevationGained -> (entry as ElevationGainedEntry).length.inMeters()
        Speed -> (entry as SpeedEntry).velocity.inKilometersPerHour()
        HeartRate -> (entry as HeartRateEntry).bpm.toDouble()
        MenstruationPeriod -> 1.0
        OxygenSaturation -> (entry as OxygenSaturationEntry).percentage.toDouble()

        Sleep -> (entry as SleepSessionEntry).let {
            when (it.stage) {
                SleepStage.Unknown,
                SleepStage.AwakeInBed,
                SleepStage.Awake,
                SleepStage.OutOfBed -> 0.0

                SleepStage.REM -> 1.0

                SleepStage.Sleeping,
                SleepStage.Light -> 2.0

                SleepStage.Deep -> 3.0
            }
        }

        HeartRateVariabilityRmssd -> (entry as HeartRateVariabilityEntry).variability
        SkinTemperature -> (entry as SkinTemperatureEntry).temperature.inCelsius()
        Symptoms -> (entry as ManualSymptomEntry).feeling.feeling.level.toDouble()
    }
}

fun DrawScope.drawMeasurementPreview(
    measurement: Measurement,
    entries: List<TimedEntry>,
    colorLine: Color,
    colorAwake: Color,
    colorREM: Color,
    colorLightSleep: Color,
    colorDeepSleep: Color,
) {
    if (entries.isEmpty()) return

    when (measurement) {
        Steps, Distance, ElevationGained -> drawPreviewAccumulated(
            measurement,
            entries,
            colorLine,
        )

        Speed,
        HeartRate -> drawPreviewLine(
            measurement,
            entries,
            colorLine,
        )

        Sleep -> drawPreviewSleep(
            // Safety: We just checked that the measurement is Sleep
            @Suppress("UNCHECKED_CAST") (entries as List<SleepSessionEntry>),
            colorAwake,
            colorREM,
            colorLightSleep,
            colorDeepSleep,
        )

        // We don't have enough data to draw sensible graphs for these
        Symptoms,
        MenstruationPeriod,
        OxygenSaturation,
        HeartRateVariabilityRmssd,
        SkinTemperature -> {
        }
    }
}

/**
 * Draws a preview graph for accumulated measurements such as steps or distance.
 *
 * Individual entries are converted to graph values and accumulated over time
 * before being rendered as a continuous line.
 *
 * @param measurement The measurement being drawn.
 * @param entries Timed entries for the measurement.
 * @param strokeColor Color used for the graph line.
 */
private fun DrawScope.drawPreviewAccumulated(
    measurement: Measurement,
    entries: List<TimedEntry>,
    strokeColor: Color,
) {
    val xData = entries.fastMap { measurement.entryToXValue(it) }
    val yData = entries
        .fastMap { measurement.entryToYValue(it) }
        .runningReduce { acc, y -> acc + y }


    val xRange = TimeRange.today().toEpochDoubleRange()
    val yRange = measurement.yRange(yData)

    drawPath(
        graphToPaths(xData, yData, size, xRange, yRange).line,
        color = strokeColor,
        style = Graph.defaultStrokeStyle(),
    )
}

/**
 * Draws a preview line graph for non-accumulated measurements.
 *
 * Entries are plotted directly over time without accumulation, producing
 * a standard time-series line graph.
 *
 * @param measurement The measurement being drawn.
 * @param entries Timed entries for the measurement.
 * @param strokeColor Color used for the graph line.
 */
private fun DrawScope.drawPreviewLine(
    measurement: Measurement,
    entries: List<TimedEntry>,
    strokeColor: Color,
) {
    val xData = entries.fastMap { measurement.entryToXValue(it) }
    val yData = entries.fastMap { measurement.entryToYValue(it) }
    val xRange = TimeRange.today().toEpochDoubleRange()
    val yRange = measurement.yRange(yData)

    drawPath(
        graphToPaths(xData, yData, size, xRange, yRange).line,
        color = strokeColor,
        style = Graph.defaultStrokeStyle(),
    )
}

/**
 * Draws a preview visualization for sleep sessions.
 *
 * Each sleep stage is rendered as a horizontal segment positioned vertically
 * according to its stage and colored by stage type. Unknown stages are skipped.
 *
 * @param entries Sleep session entries for today.
 * @param colorAwake Color used for awake-related stages.
 * @param colorREM Color used for REM sleep.
 * @param colorLightSleep Color used for light sleep.
 * @param colorDeepSleep Color used for deep sleep.
 */
private fun DrawScope.drawPreviewSleep(
    entries: List<SleepSessionEntry>,
    colorAwake: Color,
    colorREM: Color,
    colorLightSleep: Color,
    colorDeepSleep: Color,
) {
    val xRange = TimeRange.today().toEpochDoubleRange()
    val xRangeWidth = xRange.endInclusive - xRange.start

    for (entry in entries) {
        val (color, stage) = when (entry.stage) {
            SleepStage.Unknown -> continue

            SleepStage.Awake,
            SleepStage.OutOfBed,
            SleepStage.AwakeInBed -> Pair(colorAwake, 0)

            SleepStage.REM -> Pair(colorREM, 1)

            SleepStage.Sleeping,
            SleepStage.Light -> Pair(colorLightSleep, 2)

            SleepStage.Deep -> Pair(colorDeepSleep, 3)
        }

        val start = (entry.start.toEpochMilliseconds().toDouble() - xRange.start) / xRangeWidth
        val end = (entry.end.toEpochMilliseconds().toDouble() - xRange.start) / xRangeWidth

        val strokeWidth = 4f
        val yPadding = strokeWidth / 2f
        val availableHeight = size.height - strokeWidth

        val y = stage.toFloat() / 3f
        val yPx = yPadding + y * availableHeight

        val path = Path().apply {
            moveTo(start.toFloat() * size.width, yPx)
            lineTo(end.toFloat() * size.width, yPx)
        }

        drawPath(
            path,
            color = color,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}


data class MeasurementStatistic(val measurement: Measurement, val value: String?) {
    fun unit(): String? = when (this.measurement) {
        Steps -> null
        Distance -> "km"
        ElevationGained -> "m"
        Speed -> "km/h"
        HeartRate -> "bpm"
        MenstruationPeriod -> "h"
        OxygenSaturation -> "%"
        Sleep -> "h"
        HeartRateVariabilityRmssd -> null
        SkinTemperature -> "°C"
        Symptoms -> null
    }

    @Composable
    fun note(): String = when (this.measurement) {
        Steps -> stringResource(R.string.today)
        Distance -> stringResource(R.string.today)
        ElevationGained -> stringResource(R.string.today)
        Speed -> stringResource(R.string.average_today)
        HeartRate -> stringResource(R.string.average_today)
        MenstruationPeriod -> stringResource(R.string.today)
        OxygenSaturation -> stringResource(R.string.average_today)
        Sleep -> stringResource(R.string.today)
        HeartRateVariabilityRmssd -> stringResource(R.string.today)
        SkinTemperature -> stringResource(R.string.average_today)
        Symptoms -> stringResource(R.string.average_today)
    }
}

@Composable
fun accumulateStatistics(
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>
): MeasurementStatistic {
    val entries = measurements[measurement]
    if (entries.isNullOrEmpty()) {
        return MeasurementStatistic(measurement, null)
    }

    // Safety: Entries in measurements are always the correct TimedEntry for the corresponding key
    @Suppress("UNCHECKED_CAST")
    val accumulated = when (measurement) {
        Measurement.Steps -> (entries as List<StepsEntry>)
            .sumOf { it.count }
            .toString()

        Measurement.Distance -> (entries as List<DistanceEntry>)
            .sumOf { it.length.inKilometers() }
            .let { "%.1f".format(it) }

        ElevationGained -> (entries as List<ElevationGainedEntry>)
            .sumOf { it.length.inMeters() }
            .let { "%.1f".format(it) }

        Speed -> (entries as List<SpeedEntry>)
            .map { it.velocity.inKilometersPerHour() }
            .average()
            .let { "%.1f".format(it) }

        HeartRate -> (entries as List<HeartRateEntry>)
            .map { it.bpm }
            .average()
            .roundToInt()
            .toString()

        MenstruationPeriod -> (entries as List<MenstruationPeriodEntry>)
            .sumOf { (it.end - it.start).toDouble(DurationUnit.HOURS) }
            .let { "%.1f".format(it) }

        OxygenSaturation -> (entries as List<OxygenSaturationEntry>)
            .map { it.percentage.toDouble() }
            .average()
            .let { "%.1f".format(it) }

        Sleep -> (entries as List<SleepSessionEntry>)
            .sumOf {
                when (it.stage) {
                    SleepStage.Unknown,
                    SleepStage.Awake,
                    SleepStage.AwakeInBed,
                    SleepStage.OutOfBed -> 0.0

                    SleepStage.Sleeping,
                    SleepStage.Light,
                    SleepStage.Deep,
                    SleepStage.REM -> (it.end - it.start).toDouble(DurationUnit.HOURS)
                }
            }
            .let { "%.1f".format(it) }

        HeartRateVariabilityRmssd -> (entries as List<HeartRateVariabilityEntry>)
            .map { it.variability }
            .average()
            .let { "%+.1f".format(it) }

        SkinTemperature -> (entries as List<SkinTemperatureEntry>)
            .map { it.temperature.inCelsius() }
            .average()
            .let { "%.1f".format(it) }

        Symptoms -> (entries as List<ManualSymptomEntry>)
            .map { it.feeling.feeling.level }
            .average()
            .let {
                when (Feeling.fromInt(it.toInt())) {
                    Feeling.VeryBad -> stringResource(R.string.very_bad)
                    Feeling.Bad -> stringResource(R.string.bad)
                    Feeling.Good -> stringResource(R.string.good)
                    Feeling.VeryGood -> stringResource(R.string.very_good)
                }
            }
    }

    return MeasurementStatistic(measurement, accumulated)
}

data class TimeRange(val start: Instant, val end: Instant) {
    companion object {
        fun today(): TimeRange {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val startToday = today.date.atStartOfDayIn(TimeZone.currentSystemDefault())
            val endToday = startToday + 24.hours

            return TimeRange(startToday, endToday)
        }
    }

    fun toEpochDoubleRange() =
        start.toEpochMilliseconds().toDouble()..end.toEpochMilliseconds().toDouble()
}
