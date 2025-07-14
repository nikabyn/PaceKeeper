package org.htwk.pacing.backend.data_collection.health_connect

import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import kotlinx.datetime.toKotlinInstant
import org.htwk.pacing.backend.database.DistanceDao
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedDao
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityDao
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.MenstruationPeriodDao
import org.htwk.pacing.backend.database.MenstruationPeriodEntry
import org.htwk.pacing.backend.database.OxygenSaturationDao
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.SkinTemperatureDao
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionDao
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedDao
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsDao
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.Velocity

suspend fun storeRecord(db: PacingDatabase, record: Record) {
    when (record) {
        is DistanceRecord -> storeDistance(db.distanceDao(), record)
        is ElevationGainedRecord -> storeElevationGained(db.elevationGainedDao(), record)
        is HeartRateRecord -> storeHeartRate(db.heartRateDao(), record)
        is HeartRateVariabilityRmssdRecord -> storeHrv(db.heartRateVariabilityDao(), record)
        is MenstruationPeriodRecord -> storeMenstruationPeriod(db.menstruationPeriodDao(), record)
        is OxygenSaturationRecord -> storeOxygenSaturation(db.oxygenSaturationDao(), record)
        is SkinTemperatureRecord -> storeSkinTemperature(db.skinTemperatureDao(), record)
        is SleepSessionRecord -> storeSleepSession(db.sleepSessionsDao(), record)
        is SpeedRecord -> storeSpeed(db.speedDao(), record)
        is StepsRecord -> storeSteps(db.stepsDao(), record)
        else -> throw Exception("Unknown record type ${record::class}")
    }
}

private suspend fun storeDistance(dao: DistanceDao, record: DistanceRecord) {
    dao.insert(
        DistanceEntry(
            record.startTime.toKotlinInstant(),
            record.endTime.toKotlinInstant(),
            Length.meters(record.distance.inMeters),
        )
    )
}

private suspend fun storeElevationGained(dao: ElevationGainedDao, record: ElevationGainedRecord) {
    dao.insert(
        ElevationGainedEntry(
            record.startTime.toKotlinInstant(),
            record.endTime.toKotlinInstant(),
            Length.meters(record.elevation.inMeters),
        )
    )
}

private suspend fun storeHeartRate(dao: HeartRateDao, record: HeartRateRecord) {
    dao.insertMany(record.samples.map {
        HeartRateEntry(
            it.time.toKotlinInstant(),
            it.beatsPerMinute
        )
    })
}

private suspend fun storeHrv(
    dao: HeartRateVariabilityDao,
    record: HeartRateVariabilityRmssdRecord
) {
    dao.insert(
        HeartRateVariabilityEntry(
            record.time.toKotlinInstant(),
            record.heartRateVariabilityMillis
        )
    )
}

private suspend fun storeMenstruationPeriod(
    dao: MenstruationPeriodDao,
    record: MenstruationPeriodRecord
) {
    dao.insert(
        MenstruationPeriodEntry(
            record.startTime.toKotlinInstant(),
            record.endTime.toKotlinInstant(),
        )
    )
}

private suspend fun storeOxygenSaturation(
    dao: OxygenSaturationDao,
    record: OxygenSaturationRecord
) {
    dao.insert(
        OxygenSaturationEntry(
            record.time.toKotlinInstant(),
            Percentage.fromDouble(record.percentage.value)
        )
    )
}

private suspend fun storeSkinTemperature(dao: SkinTemperatureDao, record: SkinTemperatureRecord) {
    val baseline = record.baseline?.inCelsius ?: 0.0
    dao.insertMany(record.deltas.map {
        SkinTemperatureEntry(
            it.time.toKotlinInstant(),
            Temperature.celsius(baseline + it.delta.inCelsius)
        )
    })
}

private suspend fun storeSleepSession(dao: SleepSessionDao, record: SleepSessionRecord) {
    dao.insertMany(record.stages.map {
        SleepSessionEntry(
            it.startTime.toKotlinInstant(),
            it.endTime.toKotlinInstant(),
            SleepStage.fromInt(it.stage)
        )
    })
}

private suspend fun storeSpeed(dao: SpeedDao, record: SpeedRecord) {
    dao.insertMany(record.samples.map {
        SpeedEntry(
            it.time.toKotlinInstant(),
            Velocity.metersPerSecond(it.speed.inMetersPerSecond)
        )
    })
}

private suspend fun storeSteps(dao: StepsDao, record: StepsRecord) {
    dao.insert(
        StepsEntry(
            record.startTime.toKotlinInstant(),
            record.endTime.toKotlinInstant(),
            record.count
        )
    )
}