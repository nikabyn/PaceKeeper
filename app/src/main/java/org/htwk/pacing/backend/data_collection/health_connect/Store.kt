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

/**
 * Takes a bunch of Health Connect Records, converts them to a specific TimedEntry
 * and stores all of them in the database.
 *
 * Expects all Records in the list to be of the same subtype.
 * (ie. no mixing DistanceRecord and HeartRateRecord).
 */
fun storeRecords(db: PacingDatabase, records: List<Record>) {
    when (records.first()) {
        is DistanceRecord -> storeDistance(
            db.distanceDao(),
            records.filterIsInstance<DistanceRecord>(),
        )

        is ElevationGainedRecord -> storeElevationGained(
            db.elevationGainedDao(),
            records.filterIsInstance<ElevationGainedRecord>(),
        )

        is HeartRateRecord -> storeHeartRate(
            db.heartRateDao(),
            records.filterIsInstance<HeartRateRecord>(),
        )

        is HeartRateVariabilityRmssdRecord -> storeHrv(
            db.heartRateVariabilityDao(),
            records.filterIsInstance<HeartRateVariabilityRmssdRecord>(),
        )

        is MenstruationPeriodRecord -> storeMenstruationPeriod(
            db.menstruationPeriodDao(),
            records.filterIsInstance<MenstruationPeriodRecord>(),
        )

        is OxygenSaturationRecord -> storeOxygenSaturation(
            db.oxygenSaturationDao(),
            records.filterIsInstance<OxygenSaturationRecord>(),
        )

        is SkinTemperatureRecord -> storeSkinTemperature(
            db.skinTemperatureDao(),
            records.filterIsInstance<SkinTemperatureRecord>(),
        )

        is SleepSessionRecord -> storeSleepSession(
            db.sleepSessionsDao(),
            records.filterIsInstance<SleepSessionRecord>(),
        )

        is SpeedRecord -> storeSpeed(
            db.speedDao(),
            records.filterIsInstance<SpeedRecord>(),
        )

        is StepsRecord -> storeSteps(
            db.stepsDao(),
            records.filterIsInstance<StepsRecord>(),
        )

        else -> throw Exception("Unknown record type ${records.first()::class}")
    }
}

private fun storeDistance(dao: DistanceDao, records: List<DistanceRecord>) {
    dao.insertMany(
        records.map {
            DistanceEntry(
                it.startTime.toKotlinInstant(),
                it.endTime.toKotlinInstant(),
                Length.meters(it.distance.inMeters),
            )
        }
    )
}

private fun storeElevationGained(dao: ElevationGainedDao, records: List<ElevationGainedRecord>) {
    dao.insertMany(records.map {
        ElevationGainedEntry(
            it.startTime.toKotlinInstant(),
            it.endTime.toKotlinInstant(),
            Length.meters(it.elevation.inMeters),
        )
    })
}

private fun storeHeartRate(dao: HeartRateDao, records: List<HeartRateRecord>) {
    dao.insertMany(records.flatMap {
        it.samples.map { sample ->
            HeartRateEntry(
                sample.time.toKotlinInstant(),
                sample.beatsPerMinute
            )
        }
    })
}

private fun storeHrv(dao: HeartRateVariabilityDao, records: List<HeartRateVariabilityRmssdRecord>) {
    dao.insertMany(records.map {
        HeartRateVariabilityEntry(
            it.time.toKotlinInstant(),
            it.heartRateVariabilityMillis
        )
    })
}

private fun storeMenstruationPeriod(
    dao: MenstruationPeriodDao,
    records: List<MenstruationPeriodRecord>
) {
    dao.insertMany(records.map {
        MenstruationPeriodEntry(
            it.startTime.toKotlinInstant(),
            it.endTime.toKotlinInstant(),
        )
    })
}

private fun storeOxygenSaturation(
    dao: OxygenSaturationDao,
    records: List<OxygenSaturationRecord>
) {
    dao.insertMany(records.map {
        OxygenSaturationEntry(
            it.time.toKotlinInstant(),
            Percentage.fromDouble(it.percentage.value)
        )
    })
}

private fun storeSkinTemperature(dao: SkinTemperatureDao, records: List<SkinTemperatureRecord>) {
    dao.insertMany(records.flatMap {
        val baseline = it.baseline?.inCelsius ?: 0.0
        it.deltas.map { delta ->
            SkinTemperatureEntry(
                delta.time.toKotlinInstant(),
                Temperature.celsius(baseline + delta.delta.inCelsius)
            )
        }
    })
}

private fun storeSleepSession(dao: SleepSessionDao, records: List<SleepSessionRecord>) {
    dao.insertMany(records.flatMap {
        it.stages.map { stage ->
            SleepSessionEntry(
                stage.startTime.toKotlinInstant(),
                stage.endTime.toKotlinInstant(),
                SleepStage.fromInt(stage.stage)
            )
        }
    })
}

private fun storeSpeed(dao: SpeedDao, records: List<SpeedRecord>) {
    dao.insertMany(records.flatMap {
        it.samples.map {
            SpeedEntry(
                it.time.toKotlinInstant(),
                Velocity.metersPerSecond(it.speed.inMetersPerSecond)
            )
        }
    })
}

private fun storeSteps(dao: StepsDao, records: List<StepsRecord>) {
    dao.insertMany(records.map {
        StepsEntry(
            it.startTime.toKotlinInstant(),
            it.endTime.toKotlinInstant(),
            it.count
        )
    })
}