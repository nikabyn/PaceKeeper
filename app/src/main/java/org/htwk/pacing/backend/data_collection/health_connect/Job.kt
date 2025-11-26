package org.htwk.pacing.backend.data_collection.health_connect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.until
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.ReadEvent
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "HealthConnectJob"

private val delayBetweenPermissionPolls = 1.minutes
private val delayBetweenRecordSyncs = 1.minutes
private val delayBeforeJobRestart = 10.seconds

/**
 * Responsible for collecting, normalising and storing health data from Health Connect.
 * Monitors permission changes and dynamically starts or stops syncing for individual record types.
 *
 * - Syncs full record history if not recently fetched
 * - Continuously fetches incremental changes
 * - Runs in the foreground with a persistent notification to comply with long-running task requirements
 * - Automatically retries jobs to ensure continuous operation
 *
 * This job should never complete and has to restart if it does.
 */
suspend fun syncWithHealthConnect(applicationContext: Context, db: PacingDatabase) =
    coroutineScope {
        val client = HealthConnectClient.getOrCreate(applicationContext)

        val permissionChanges = producePermissionChanges(client)
        val jobCommands = produceJobCommands(permissionChanges)
        scheduleJobs(jobCommands) { recordType ->
            syncRecordHistory(client, db, recordType)
            syncRecordChanges(client, db, recordType)
        }
    }

private data class PermissionChange(val event: PermissionEvent, val permission: String)
private enum class PermissionEvent { Added, Removed }

/**
 * Polls the permissions granted by health connect,
 * diffs them against the previous permission
 * and emits events for added/removed permissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.producePermissionChanges(client: HealthConnectClient): ReceiveChannel<PermissionChange> =
    produce {
        var currentPermissions: Set<String>
        var previousPermissions = emptySet<String>()

        while (true) {
            try {
                currentPermissions = client.permissionController.getGrantedPermissions()

                val added = currentPermissions - previousPermissions
                for (permission in added) {
                    send(PermissionChange(PermissionEvent.Added, permission))
                }
                val removed = previousPermissions - currentPermissions
                for (permission in removed) {
                    send(PermissionChange(PermissionEvent.Removed, permission))
                }

                previousPermissions = currentPermissions
            } catch (e: Exception) {
                Log.e(TAG, "Error while polling HealthConnect permissions: $e")
            }
            delay(delayBetweenPermissionPolls)
        }
    }


/**
 * Maps a permission change to a start/stop command for the job of the corresponding Record.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.produceJobCommands(
    permissionChanges: ReceiveChannel<PermissionChange>
): Channel<JobCommand> {
    val jobEvents = Channel<JobCommand>()

    launch {
        permissionChanges.consumeEach { change ->
            val permission = change.permission
            val recordsEntry = Records.wanted.find { record -> record.readPermission == permission }
                ?: return@consumeEach
            val recordType = recordsEntry.recordType

            Log.d(TAG, change.toString())

            when (change.event) {
                PermissionEvent.Added ->
                    jobEvents.send(JobCommand(JobAction.Start, recordType))

                PermissionEvent.Removed ->
                    jobEvents.send(JobCommand(JobAction.Stop, recordType))
            }
        }
    }

    return jobEvents
}

private data class JobCommand(val action: JobAction, val recordType: KClass<out Record>)
private enum class JobAction { Start, Stop }

/**
 * Starts/stops the job for the corresponding Record.
 */
private suspend fun CoroutineScope.scheduleJobs(
    jobCommands: Channel<JobCommand>,
    work: suspend (recordType: KClass<out Record>) -> Unit
) {
    val currentPermissions = mutableSetOf<String>()
    val jobs = mutableMapOf<KClass<out Record>, Job>()

    suspend fun tryRestarting(recordType: KClass<out Record>) {
        if (currentPermissions.contains(HealthPermission.getReadPermission(recordType))) {
            delay(delayBeforeJobRestart)
            jobCommands.send(JobCommand(JobAction.Start, recordType))
        }
    }

    jobCommands.consumeEach { event ->
        val recordType = event.recordType

        when (event.action) {
            JobAction.Start -> {
                val job = launch {
                    try {
                        work(recordType)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error while executing work for $recordType:", e)
                    }
                }

                job.invokeOnCompletion {
                    Log.w(TAG, "Job for ${recordType.simpleName} completed")
                    jobs.remove(recordType)
                    launch { tryRestarting(recordType) }
                }

                Log.i(TAG, "Job for ${recordType.simpleName} started")
                jobs[recordType] = job
            }

            JobAction.Stop -> {
                jobs[recordType]?.cancelAndJoin()
                jobs.remove(recordType)
            }
        }
    }
}

/**
 * Continuously inserts new data of the provided Record into the database.
 */
private suspend inline fun syncRecordChanges(
    client: HealthConnectClient,
    db: PacingDatabase,
    recordType: KClass<out Record>
) {
    var changesToken = client.getChangesToken(
        ChangesTokenRequest(setOf(recordType))
    )
    while (true) {
        val changesResponse = client.getChanges(changesToken)
        val newRecords = changesResponse.changes.mapNotNull {
            if (it is UpsertionChange) it.record else null
        }
        db.readEventDao().insert(ReadEvent(Clock.System.now(), recordType.simpleName!!))
        if (newRecords.isNotEmpty()) {
            Log.d(TAG, "Received records: $newRecords")
            storeRecords(db, newRecords)
        }
        changesToken = changesResponse.nextChangesToken
        delay(delayBetweenRecordSyncs)
    }
}

/**
 * Reads and stores the history of a Record that we do not yet have stored in our database.
 */
private suspend inline fun syncRecordHistory(
    client: HealthConnectClient,
    db: PacingDatabase,
    recordType: KClass<out Record>
) {
    val lastEvent = db.readEventDao().getOfRecord(recordType.simpleName!!).lastOrNull()
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
        db.readEventDao().insert(ReadEvent(end, recordType.simpleName!!))
        storeRecords(db, history)
    }
}