package org.htwk.pacing.backend.data_collection

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

object HealthConnectHelper {
    private const val TAG = "HealthConnectHelper"
    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    fun readHeartRateData(context: Context) {
        val client = HealthConnectClient.getOrCreate(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endTime = Instant.now()
                val startTime = endTime.minusSeconds(60 * 60 * 24) // letzte 24h

                // Einzelne Samples lesen
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
                for (record in response.records) {
                    val origin = record.metadata.dataOrigin.packageName
                    val samples = record.samples.joinToString(", ") { "${it.beatsPerMinute} bpm" }
                    Log.d(TAG, "Samples: [$samples] @ ${record.startTime} von $origin")
                }

                // Durchschnitt berechnen
                val aggregate = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(HeartRateRecord.BPM_AVG),
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
                val avgBpm = aggregate[HeartRateRecord.BPM_AVG]
                Log.d(TAG, "Durchschnittliche Herzfrequenz (24h): $avgBpm")

            } catch (e: SecurityException) {
                Log.e(TAG, "Health Connect Berechtigung fehlt", e)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Lesen der HR-Daten", e)
            }
        }
    }

    fun getRequiredPermissions() = permissions
}
