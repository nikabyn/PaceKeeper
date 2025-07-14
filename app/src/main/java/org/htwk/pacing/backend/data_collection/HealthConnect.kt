package org.htwk.pacing.backend.data_collection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
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
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.MenstruationPeriodEntry
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.Velocity
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object Permissions {
    inline fun <reified T : Record> read() = HealthPermission.getReadPermission<T>()
    inline fun <reified T : Record> write() = HealthPermission.getWritePermission<T>()

    val records = setOf(
        DistanceRecord::class,
        ElevationGainedRecord::class,
        HeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        MenstruationPeriodRecord::class,
        OxygenSaturationRecord::class,
        SkinTemperatureRecord::class,
        SleepSessionRecord::class,
        SpeedRecord::class,
        StepsRecord::class,
    )

    val wanted = setOf(
        read<DistanceRecord>(),
        read<ElevationGainedRecord>(),
        read<HeartRateRecord>(),
        read<HeartRateVariabilityRmssdRecord>(),
        read<MenstruationPeriodRecord>(),
        read<OxygenSaturationRecord>(),
        read<SkinTemperatureRecord>(),
        read<SleepSessionRecord>(),
        read<SpeedRecord>(),
        read<StepsRecord>(),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    )
}

class HealthConnectWorker(
    context: Context,
    workerParams: WorkerParameters,
    db: PacingDatabase,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "HealthConnectWorker"
    }

    private val client = HealthConnectClient.getOrCreate(context)
    private val distanceDao = db.distanceDao()
    private val elevationGainedDao = db.elevationGainedDao()
    private val heartRateDao = db.heartRateDao()
    private val heartRateVariabilityDao = db.heartRateVariabilityDao()
    private val menstruationPeriodDao = db.menstruationPeriodDao()
    private val oxygenSaturationDao = db.oxygenSaturationDao()
    private val skinTemperatureDao = db.skinTemperatureDao()
    private val sleepSessionsDao = db.sleepSessionsDao()
    private val speedDao = db.speedDao()
    private val stepsDao = db.stepsDao()

    private suspend fun grantedPermissions() =
        client.permissionController.getGrantedPermissions()

    val daoOfRecord = mapOf(
        DistanceRecord::class to distanceDao,
        ElevationGainedRecord::class to elevationGainedDao,
        HeartRateRecord::class to heartRateDao,
        HeartRateVariabilityRmssdRecord::class to heartRateVariabilityDao,
        MenstruationPeriodRecord::class to menstruationPeriodDao,
        OxygenSaturationRecord::class to oxygenSaturationDao,
        SkinTemperatureRecord::class to skinTemperatureDao,
        SleepSessionRecord::class to sleepSessionsDao,
        SpeedRecord::class to speedDao,
        StepsRecord::class to stepsDao,
    )

    override suspend fun doWork(): Result {
        setForegroundAsync(getForegroundInfo())

        for ((recordType, dao) in daoOfRecord) {
            val readPermission = HealthPermission.getReadPermission(recordType)
            if (!grantedPermissions().contains(readPermission)) {
                Log.d(TAG, "Skipped reading history for $recordType")
                continue
            }

            val since = dao.getLatest()?.end ?: Clock.System.now().minus(30.days)
            val records = getHistory(recordType, since)
            for (record in records) {
                storeRecord(record)
            }
        }

        val jobs = mutableMapOf<KClass<out Record>, Job>()
        while (true) {
            for (recordType in Permissions.records) {
                val permission = HealthPermission.getReadPermission(recordType)

                if (grantedPermissions().contains(permission)) {
                    if (jobs.contains(recordType)) continue

                    val job = CoroutineScope(Dispatchers.IO).launch {
                        syncChanges(recordType)
                    }
                    job.invokeOnCompletion {
                        // Log.w(TAG, "Job for ${recordType.simpleName} completed")
                        jobs.remove(recordType)
                    }

                    Log.i(TAG, "Job for ${recordType.simpleName} started")
                    jobs.put(recordType, job)
                } else {
                    // Log.w(TAG, "Job for ${recordType.simpleName} canceled, missing permissions")
                    jobs.remove(recordType)
                }
            }

            delay(10_000)
        }

        // Always restart worker if we get here
        return Result.retry()
    }

    private suspend inline fun syncChanges(recordType: KClass<out Record>) {
        var changesToken = client.getChangesToken(
            ChangesTokenRequest(setOf(recordType))
        )
        while (true) {
            val changesResponse = client.getChanges(changesToken)
            val newRecords = changesResponse.changes.mapNotNull {
                Log.d(TAG, "Change: $it")
                if (it is UpsertionChange) it.record else null
            }
            if (newRecords.isNotEmpty()) {
                Log.d(TAG, "Received records: $newRecords")
            }
            for (record in newRecords) {
                storeRecord(record)
            }
            changesToken = changesResponse.nextChangesToken
            delay(1.minutes.inWholeMilliseconds)
        }
    }

    private suspend fun storeRecord(record: Record) {
        when (record) {
            is DistanceRecord -> storeDistance(record)
            is ElevationGainedRecord -> storeElevationGained(record)
            is HeartRateRecord -> storeHeartRate(record)
            is HeartRateVariabilityRmssdRecord -> storeHeartRateVariability(record)
            is MenstruationPeriodRecord -> storeMenstruationPeriod(record)
            is OxygenSaturationRecord -> storeOxygenSaturation(record)
            is SkinTemperatureRecord -> storeSkinTemperature(record)
            is SleepSessionRecord -> storeSleepSession(record)
            is SpeedRecord -> storeSpeed(record)
            is StepsRecord -> storeSteps(record)
            else -> throw Exception("Unknown record type ${record::class}")
        }
    }

    private suspend fun storeDistance(record: DistanceRecord) {
        distanceDao.insert(
            DistanceEntry(
                record.startTime.toKotlinInstant(),
                record.endTime.toKotlinInstant(),
                Length.meters(record.distance.inMeters),
            )
        )
    }

    private suspend fun storeElevationGained(record: ElevationGainedRecord) {
        elevationGainedDao.insert(
            ElevationGainedEntry(
                record.startTime.toKotlinInstant(),
                record.endTime.toKotlinInstant(),
                Length.meters(record.elevation.inMeters),
            )
        )

    }

    private suspend fun storeHeartRate(record: HeartRateRecord) {
        heartRateDao.insertMany(record.samples.map {
            HeartRateEntry(
                it.time.toKotlinInstant(),
                it.beatsPerMinute
            )
        })
    }

    private suspend fun storeHeartRateVariability(record: HeartRateVariabilityRmssdRecord) {
        heartRateVariabilityDao.insert(
            HeartRateVariabilityEntry(
                record.time.toKotlinInstant(),
                record.heartRateVariabilityMillis
            )
        )
    }

    private suspend fun storeMenstruationPeriod(record: MenstruationPeriodRecord) {
        menstruationPeriodDao.insert(
            MenstruationPeriodEntry(
                record.startTime.toKotlinInstant(),
                record.endTime.toKotlinInstant(),
            )
        )
    }

    private suspend fun storeOxygenSaturation(record: OxygenSaturationRecord) {
        oxygenSaturationDao.insert(
            OxygenSaturationEntry(
                record.time.toKotlinInstant(),
                Percentage.fromDouble(record.percentage.value)
            )
        )
    }

    private suspend fun storeSkinTemperature(record: SkinTemperatureRecord) {
        val baseline = record.baseline?.inCelsius ?: 0.0
        skinTemperatureDao.insertMany(record.deltas.map {
            SkinTemperatureEntry(
                it.time.toKotlinInstant(),
                Temperature.celsius(baseline + it.delta.inCelsius)
            )
        })
    }

    private suspend fun storeSleepSession(record: SleepSessionRecord) {
        sleepSessionsDao.insertMany(record.stages.map {
            SleepSessionEntry(
                it.startTime.toKotlinInstant(),
                it.endTime.toKotlinInstant(),
                SleepStage.fromInt(it.stage)
            )
        })
    }

    private suspend fun storeSpeed(record: SpeedRecord) {
        speedDao.insertMany(record.samples.map {
            SpeedEntry(
                it.time.toKotlinInstant(),
                Velocity.metersPerSecond(it.speed.inMetersPerSecond)
            )
        })
    }


    private suspend fun storeSteps(record: StepsRecord) {
        stepsDao.insert(
            StepsEntry(
                record.startTime.toKotlinInstant(),
                record.endTime.toKotlinInstant(),
                record.count
            )
        )
    }

    private suspend inline fun getHistory(
        recordType: KClass<out Record>, since: Instant,
    ): List<Record> {
        val history = mutableListOf<Record>()
        var nextPageToken: String? = null
        val pageSize = 1000

        do {
            val request = ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = TimeRangeFilter.between(
                    since.toJavaInstant(),
                    Clock.System.now().toJavaInstant()
                ),
                pageSize = pageSize,
                pageToken = nextPageToken
            )
            val response = client.readRecords(request)
            history.addAll(response.records)
            Log.d(TAG, "History: ${response.records}")

            if (response.records.size < pageSize)
                break

            nextPageToken = response.pageToken
            Log.d(TAG, "Next: $nextPageToken")
        } while (nextPageToken != null)

        return history.toList()
    }

    private fun createNotification(): Notification {
        val channelId = "health_connect_sync_ch"
        val channel =
            NotificationChannel(
                channelId,
                "Health Connect Sync",
                NotificationManager.IMPORTANCE_LOW
            )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Reading data from Health Connect…")
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, createNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, createNotification())
        }
    }
}