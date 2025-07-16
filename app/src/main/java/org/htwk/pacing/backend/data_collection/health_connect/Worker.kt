package org.htwk.pacing.backend.data_collection.health_connect

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
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.until
import org.htwk.pacing.R
import org.htwk.pacing.backend.NotificationIds
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.ReadEvent
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Foreground worker responsible for collecting, normalising and storing health data from Health Connect.
 * Monitors permission changes and dynamically starts or stops syncing for individual record types.
 *
 * - Syncs full record history if not recently fetched
 * - Continuously fetches incremental changes
 * - Runs in the foreground with a persistent notification to comply with long-running task requirements
 * - Automatically retries jobs to ensure continuous operation
 *
 * This worker should never complete and has to restart if it does.
 */
class HealthConnectWorker(
    context: Context,
    workerParams: WorkerParameters,
    val db: PacingDatabase,
) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "HealthConnectWorker"
    }

    private val readEventDao = db.readEventDao()
    private val client = HealthConnectClient.getOrCreate(context)

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())


        coroutineScope {
            HealthConnectJobScheduler(client, this) { recordType ->
                syncRecordHistory(recordType)
                syncRecordChanges(recordType)
            }.run()
        }

        // Always restart worker if we get here
        return Result.retry()
    }


    private suspend inline fun syncRecordChanges(recordType: KClass<out Record>) {
        var changesToken = client.getChangesToken(
            ChangesTokenRequest(setOf(recordType))
        )
        while (true) {
            val changesResponse = client.getChanges(changesToken)
            val newRecords = changesResponse.changes.mapNotNull {
                if (it is UpsertionChange) it.record else null
            }
            readEventDao.insert(ReadEvent(Clock.System.now(), recordType.simpleName!!))
            if (newRecords.isNotEmpty()) {
                Log.d(TAG, "Received records: $newRecords")
                storeRecords(db, newRecords)
            }
            changesToken = changesResponse.nextChangesToken
            delay(1.minutes.inWholeMilliseconds)
        }
    }

    private suspend inline fun syncRecordHistory(recordType: KClass<out Record>) {
        val lastEvent = readEventDao.getOfRecord(recordType.simpleName!!).lastOrNull()
        Log.d(TAG, "Last event $lastEvent")

        val end = Clock.System.now()
        val start = lastEvent?.time ?: (end - 30.days)
        // Do not read history if we have read in the last 1 hour
        if (start > end - 1.hours) return

        Log.d(TAG, "Read ${start.until(end, DateTimeUnit.MILLISECOND).milliseconds}")
        val history = mutableListOf<Record>()
        var nextPageToken: String? = null
        val pageSize = 1000

        do {
            val request = ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = TimeRangeFilter.between(
                    start.toJavaInstant(),
                    end.toJavaInstant()
                ),
                pageSize = pageSize,
                pageToken = nextPageToken,
            )
            val response = client.readRecords(request)
            history.addAll(response.records)

            if (response.records.size < pageSize)
                break

            nextPageToken = response.pageToken
        } while (nextPageToken != null)

        if (history.isNotEmpty()) {
            Log.d(TAG, "History $history")
            readEventDao.insert(ReadEvent(end, recordType.simpleName!!))
            storeRecords(db, history)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = NotificationIds.HEALTH_CONNECT_SYNC_CHANNEL_ID
        val notificationId = NotificationIds.HEALTH_CONNECT_SYNC_NOTIFICATION_ID

        val channel = NotificationChannel(
            NotificationIds.HEALTH_CONNECT_SYNC_CHANNEL_ID,
            applicationContext.getString(R.string.health_connect_sync),
            NotificationManager.IMPORTANCE_LOW
        )
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.collecting_health_data))
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

}

class HealthConnectJobScheduler(
    val client: HealthConnectClient,
    val scope: CoroutineScope,
    val job: suspend (recordType: KClass<out Record>) -> Unit
) {
    companion object {
        const val TAG = "HealthConnectJobScheduler"
    }

    data class PermissionChange(val event: PermissionEvent, val permission: String)
    enum class PermissionEvent { Added, Removed }

    var currentPermissions = emptySet<String>()
    val permissionChanges = MutableSharedFlow<PermissionChange>(replay = 0)
    val jobs = mutableMapOf<KClass<out Record>, Job>()

    fun run() {
        scope.launch { consumePermissionChanges() }
        scope.launch { emitPermissionChanges() }
    }

    suspend fun consumePermissionChanges() {
        permissionChanges.collect {
            val permission = it.permission
            val recordsEntry = Records.wanted.find { it.readPermission == permission }
                ?: return@collect
            val recordType = recordsEntry.recordType

            Log.d(TAG, it.toString())

            when (it.event) {
                PermissionEvent.Added -> {
                    launchRecordSyncJob(recordType)
                }

                PermissionEvent.Removed -> {
                    jobs.remove(recordType)
                }
            }
        }
    }

    suspend fun emitPermissionChanges() {
        var previousPermissions = emptySet<String>()
        while (true) {
            try {
                currentPermissions = client.permissionController.getGrantedPermissions()
                val added = currentPermissions - previousPermissions
                for (permission in added) {
                    permissionChanges.emit(
                        PermissionChange(
                            PermissionEvent.Added,
                            permission
                        )
                    )
                    Log.d(TAG, "send")
                }

                val removed = previousPermissions - currentPermissions
                for (permission in removed) {
                    permissionChanges.emit(
                        PermissionChange(
                            PermissionEvent.Removed,
                            permission
                        )
                    )
                }

                previousPermissions = currentPermissions
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
            delay(1.minutes.inWholeMilliseconds)
        }
    }

    fun launchRecordSyncJob(recordType: KClass<out Record>) {
        val job = scope.launch {
            try {
                job(recordType)
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
        job.invokeOnCompletion {
            Log.w(TAG, "Job for ${recordType.simpleName} completed")
            jobs.remove(recordType)
            if (currentPermissions.contains(HealthPermission.getReadPermission(recordType))) {
                scope.launch {
                    delay(10.seconds.inWholeMilliseconds)
                    launchRecordSyncJob(recordType)
                }
            }
        }
        Log.i(TAG, "Job for ${recordType.simpleName} started")
        jobs.put(recordType, job)
    }
}