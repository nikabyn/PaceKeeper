package org.htwk.pacing.backend.data_collection.health_connect

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
import androidx.health.connect.client.records.Record
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
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class HealthConnectWorker(
    context: Context,
    workerParams: WorkerParameters,
    val db: PacingDatabase,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        setForegroundAsync(getForegroundInfo())

        for ((recordType, _, _, getDao) in Records.wanted) {
            val readPermission = HealthPermission.getReadPermission(recordType)
            if (!grantedPermissions().contains(readPermission)) {
                Log.d(TAG, "Skipped reading history for $recordType")
                continue
            }

            val since = getDao(db).getLatest()?.end ?: (Clock.System.now() - 30.days)
            readRecordHistory(recordType, since).forEach {
                storeRecord(db, it)
            }
        }

        val jobs = mutableMapOf<KClass<out Record>, Job>()
        while (true) {
            for ((recordType, readPermission, _, _) in Records.wanted) {
                if (grantedPermissions().contains(readPermission)) {
                    if (jobs.contains(recordType)) continue

                    val job = CoroutineScope(Dispatchers.IO).launch {
                        syncRecordChanges(recordType)
                    }
                    job.invokeOnCompletion {
                        Log.w(TAG, "Job for ${recordType.simpleName} completed")
                        jobs.remove(recordType)
                    }

                    Log.i(TAG, "Job for ${recordType.simpleName} started")
                    jobs.put(recordType, job)
                } else {
                    Log.w(TAG, "Job for ${recordType.simpleName} canceled, missing permissions")
                    jobs.remove(recordType)
                }
            }

            delay(10_000)
        }

        // Always restart worker if we get here
        return Result.retry()
    }

    private val client = HealthConnectClient.getOrCreate(context)
    private suspend fun grantedPermissions() =
        client.permissionController.getGrantedPermissions()


    private suspend inline fun syncRecordChanges(recordType: KClass<out Record>) {
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
                storeRecord(db, record)
            }
            changesToken = changesResponse.nextChangesToken
            delay(1.minutes.inWholeMilliseconds)
        }
    }

    private suspend inline fun readRecordHistory(
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
            .setContentTitle("Collecting Health Data")
            .setSmallIcon(R.drawable.rounded_monitor_heart_24)
            .setOngoing(true)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, createNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, createNotification())
        }
    }

    companion object {
        private const val TAG = "HealthConnectWorker"
    }
}