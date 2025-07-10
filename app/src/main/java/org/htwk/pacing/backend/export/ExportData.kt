package org.htwk.pacing.backend.export

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.*

data class ExportData(
    val timestamp: Instant,
    val values: Map<String, String>
)

fun HeartRateEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("HeartRate" to bpm.toString())
)

fun DistanceEntry.toExportData() = ExportData(
    timestamp = start,
    values = mapOf("DistanceMeters" to length.inMeters().toString())
)

fun ElevationGainedEntry.toExportData() = ExportData(
    timestamp = start,
    values = mapOf("ElevationGainedMeters" to length.inMeters().toString())
)

fun EnergyLevelEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("EnergyLevel" to percentage.toString())
)

fun HeartRateVariabilityEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("HRV" to variability.toString())
)

fun MenstruationPeriodEntry.toExportData() = ExportData(
    timestamp = start,
    values = mapOf("MenstruationEnd" to end.toString())
)

fun OxygenSaturationEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("OxygenSaturation" to percentage.toString())
)

fun SkinTemperatureEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("SkinTemperatureCelsius" to temperature.inCelsius().toString())
)

fun SleepSessionEntry.toExportData() = ExportData(
    timestamp = start,
    values = mapOf(
        "SleepEnd" to end.toString(),
        "SleepStage" to stage.toString()
    )
)

fun SpeedEntry.toExportData() = ExportData(
    timestamp = time,
    values = mapOf("SpeedMetersPerSecond" to velocity.inMetersPerSecond().toString())
)

fun StepsEntry.toExportData() = ExportData(
    timestamp = start,
    values = mapOf(
        "StepsEnd" to end.toString(),
        "StepsCount" to count.toString()
    )
)