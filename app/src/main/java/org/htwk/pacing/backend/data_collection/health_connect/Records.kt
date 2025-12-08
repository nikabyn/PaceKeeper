package org.htwk.pacing.backend.data_collection.health_connect

import androidx.health.connect.client.permission.HealthPermission
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
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.backend.database.TimedSeries
import kotlin.reflect.KClass

object Records {
    val wanted = setOf(
        createEntry<DistanceRecord> { it.distanceDao() },
        createEntry<ElevationGainedRecord> { it.elevationGainedDao() },
        createEntry<HeartRateRecord> { it.heartRateDao() },
        createEntry<HeartRateVariabilityRmssdRecord> { it.heartRateVariabilityDao() },
        createEntry<MenstruationPeriodRecord> { it.menstruationPeriodDao() },
        createEntry<OxygenSaturationRecord> { it.oxygenSaturationDao() },
        createEntry<SkinTemperatureRecord> { it.skinTemperatureDao() },
        createEntry<SleepSessionRecord> { it.sleepSessionsDao() },
        createEntry<SpeedRecord> { it.speedDao() },
        createEntry<StepsRecord> { it.stepsDao() },
    )

    @ConsistentCopyVisibility
    data class Entry internal constructor(
        val recordType: KClass<out Record>,
        val readPermission: String,
        val writePermission: String,
        val getDao: (db: PacingDatabase) -> TimedSeries<out TimedEntry>
    )

    internal inline fun <reified T : Record> createEntry(
        noinline getDao: (db: PacingDatabase) -> TimedSeries<out TimedEntry>
    ) = Entry(
        T::class,
        HealthPermission.getReadPermission<T>(),
        HealthPermission.getWritePermission<T>(),
        getDao
    )
}

val wantedPermissions = Records.wanted.map { it.readPermission }
    .union(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))
