package org.htwk.pacing.ui.screens.measurements

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
import kotlin.math.roundToInt
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

    fun toGraphValue(entry: TimedEntry): Pair<Double, Double> = Pair(
        entry.end.toEpochMilliseconds().toDouble(),
        when (this) {
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
    )

    fun yRange(yData: List<Double>): ClosedRange<Double> {
        val yMax = if (yData.isEmpty()) null else yData.max()
        return when (this) {
            Steps -> 0.0..(yMax ?: 1_000.0)
            Distance -> 0.0..(yMax ?: 10.0)
            ElevationGained -> 0.0..(yMax ?: 100.0)
            Speed -> 0.0..(yMax ?: 10.0)
            HeartRate -> 0.0..(yMax ?: 150.0)
            MenstruationPeriod -> 0.0..24.0
            OxygenSaturation -> 0.0..1.0
            Sleep -> 0.0..3.0
            HeartRateVariabilityRmssd -> 0.0..1.0
            SkinTemperature -> -20.0..60.0
            Symptoms -> 0.0..3.0
        }
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
