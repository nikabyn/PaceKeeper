package org.htwk.pacing.backend.data_collection.health_connect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

/**
 * Zentrale Hilfsklasse zum Zugriff auf Health Connect-Daten.
 * Bietet sowohl aggregierte als auch rohe Herzfrequenz- und Schrittzahl-Daten.
 * Kapselt Health Connect API vollständig.
 */
object HealthConnectHelper {
    private const val TAG = "HealthConnectHelper"

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    /**
     * Aggregierte Herzfrequenz (bpm Ø) im Zeitbereich.
     */
    suspend fun readAvgHeartRate(context: Context, from: Instant, to: Instant): Double? {
        val client = HealthConnectClient.getOrCreate(context)
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        val bpm = result[HeartRateRecord.BPM_AVG]?.toDouble()
        Log.d(TAG, "Avg HR [$from – $to]: $bpm")
        return bpm
    }

    /**
     * Herzfrequenz-Samples (Zeit + bpm) im Zeitbereich.
     */
    suspend fun readHeartRateSamples(
        context: Context,
        from: Instant,
        to: Instant
    ): List<Pair<String, Long>> {
        val client = HealthConnectClient.getOrCreate(context)
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )

        val samples = records.records.flatMap { record ->
            record.samples.map {
                val timeStr =
                    record.startTime.atZone(ZoneId.systemDefault()).toLocalTime().toString()
                timeStr to it.beatsPerMinute
            }
        }

        Log.d(TAG, "Fetched ${samples.size} HR samples between $from and $to")
        return samples
    }

    /**
     * Schrittanzahl im gegebenen Zeitbereich (aggregiert).
     */
    suspend fun readStepsCount(context: Context, from: Instant, to: Instant): Long? {
        val client = HealthConnectClient.getOrCreate(context)
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        val count = result[StepsRecord.COUNT_TOTAL]
        Log.d(TAG, "Steps [$from – $to]: $count")
        return count
    }

    /**
     * Schrittanzahl im Zeitbereich (alle Einzelwerte, summiert).
     */
    suspend fun readStepsSum(context: Context, from: Instant, to: Instant): Long {
        val client = HealthConnectClient.getOrCreate(context)
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        val total = records.records.sumOf { it.count }
        Log.d(TAG, "Total step count [$from – $to]: $total")
        return total
    }

    /**
     * Herzfrequenzdaten in Health Connect einfügen (batchweise, robust gegen Fehler).
     */
    suspend fun insertHeartRateRecords(context: Context, records: List<HeartRateRecord>): Int {
        val client = HealthConnectClient.getOrCreate(context)
        val batchSize = 500
        var totalInserted = 0

        records.chunked(batchSize).forEach { batch ->
            try {
                client.insertRecords(batch)
                totalInserted += batch.size
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Batch Insert: ${e.message}", e)
            }
        }

        Log.d(TAG, "Insgesamt $totalInserted Herzfrequenz-Datensätze eingefügt.")
        return totalInserted
    }
}

