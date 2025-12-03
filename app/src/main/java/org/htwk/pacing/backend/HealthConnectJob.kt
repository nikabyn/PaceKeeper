package org.htwk.pacing.backend

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
import org.htwk.pacing.backend.data_collection.health_connect.Records
import org.htwk.pacing.backend.data_collection.health_connect.storeRecords
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.ReadEvent
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Responsible for collecting, normalising and storing health data from Health Connect.
 * Monitors permission changes and dynamically starts or stops syncing for individual record types.
 *
 * - Syncs full record history if not recently fetched
 * - Continuously fetches incremental changes
 * - Automatically retries jobs to ensure continuous operation
 *
 * This job should never complete and has to restart if it does.
 *
 * ```
 * ┌────────────────────────────┐
 * │ producePermissionChanges() │
 * └────────────────────────────┘
 *               │
 *       PermissionChange
 *               v
 *    ┌──────────────────────┐
 *    │ produceJobCommands() │
 *    └──────────────────────┘
 *               │
 *          JobCommand <─────────┐
 *               v               │
 *       ┌────────────────┐      │
 *       │ scheduleJobs() │──────┘
 *       └────────────────┘
 * ```
 */
object HealthConnectJob {
    const val TAG = "HealthConnectJob"

    private val delayBetweenPermissionPolls = 1.minutes
    private val delayBetweenRecordSyncs = 1.minutes
    private val delayBeforeJobRestart = 10.seconds

    /**
     * Entry point for the [HealthConnectJob].
     *
     * Initializes the Health Connect client, listens for permission changes,
     * maps them to job commands, and schedules continuous syncing for each record type.
     *
     * This function is designed to run indefinitely. Jobs are automatically restarted
     * if they complete or fail.
     *
     * @param context Context used to create the [HealthConnectClient].
     * @param db Used for storing and retrieving health records and events.
     *
     * @throws Exception if the initialization of HealthConnectClient fails.
     */
    suspend fun run(context: Context, db: PacingDatabase) = coroutineScope {
        val client = HealthConnectClient.getOrCreate(context)

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
     * Continuously polls granted permissions from Health Connect and emits changes.
     *
     * Compares current permissions with the previous set and sends events for:
     *  - Permissions added
     *  - Permissions removed
     *
     * The channel never closes and handles exceptions internally.
     *
     * @param client The [HealthConnectClient] used to query permissions.
     * @return Channel emitting [PermissionChange]s.
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
     * Maps permission changes to job commands for the corresponding record type.
     *
     * - Adds a Start command when a new permission is granted.
     * - Adds a Stop command when a permission is removed.
     *
     * The channel only closes when [permissionChanges] closes.
     *
     * @param permissionChanges Channel of permission changes to process.
     * @return Channel emitting [JobCommand]s.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.produceJobCommands(
        permissionChanges: ReceiveChannel<PermissionChange>
    ): Channel<JobCommand> {
        val jobEvents = Channel<JobCommand>()

        launch {
            permissionChanges.consumeEach { change ->
                val permission = change.permission
                val recordsEntry =
                    Records.wanted.find { record -> record.readPermission == permission }
                        ?: return@consumeEach
                val recordType = recordsEntry.recordType

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
     * Schedules jobs based on incoming [JobCommand]s.
     *
     * - Starts jobs when a Start command is received.
     * - Cancels jobs when a Stop command is received.
     * - Automatically retries jobs if they complete unexpectedly.
     *
     * Behavior:
     * - Each job runs continuously for its record type.
     * - Jobs are automatically restarted after a short delay if they terminate.
     * - Exceptions inside `work` are logged but do not stop the job loop.
     *
     * @param jobCommands Channel emitting job commands.
     * @param work Suspended closure executed for each record type when a job starts.
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
     * Continuously fetches incremental changes for the specified record type and stores them in the database.
     *
     * - Uses a changes token to fetch only new records.
     * - Stores a ReadEvent for every batch of new records.
     * - Stores the data of those records.
     * - Runs indefinitely with a short delay between syncs.
     *
     * @param client HealthConnectClient used to fetch record changes.
     * @param db PacingDatabase used to store fetched records.
     * @param recordType Type of record to sync.
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
     * Fetches the historical data for a record type that is not yet in the database.
     *
     * - Fetches data in pages of up to 1000 records.
     * - Skips fetching if data has been read within the last hour.
     * - Stores all fetched records and a ReadEvent.
     *
     * @param client HealthConnectClient used to read historical records.
     * @param db PacingDatabase used to store records and read events.
     * @param recordType Type of record to fetch history for.
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
}