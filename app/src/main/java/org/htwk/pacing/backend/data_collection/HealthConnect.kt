package org.htwk.pacing.backend.data_collection

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
import java.time.ZonedDateTime

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
    suspend fun readHeartRateSamples(context: Context, from: Instant, to: Instant): List<Pair<String, Long>> {
        val client = HealthConnectClient.getOrCreate(context)
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )

        val samples = records.records.flatMap { record ->
            record.samples.map {
                val timeStr = record.startTime.atZone(ZoneId.systemDefault()).toLocalTime().toString()
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
     * Liste aller Herzfrequenzdaten der letzten X Tage – für Debug oder Analysezwecke.
     */
    suspend fun debugAllHeartRates(context: Context, days: Long = 10) {
        val client = HealthConnectClient.getOrCreate(context)
        val endTime = Instant.now()
        val startTime = endTime.minusSeconds(days * 86400)

        val allRecords = mutableListOf<HeartRateRecord>()
        var nextPageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = nextPageToken
                )
            )
            allRecords += response.records
            nextPageToken = response.pageToken
        } while (nextPageToken != null)

        for (record in allRecords) {
            val origin = record.metadata.dataOrigin.packageName
            val samples = record.samples.joinToString(", ") { "${it.beatsPerMinute} bpm" }
            Log.d(TAG, "Samples: [$samples] @ ${record.startTime} von $origin")
        }

        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        val avgBpm = aggregate[HeartRateRecord.BPM_AVG]
        Log.d(TAG, "Durchschnittliche Herzfrequenz ($days Tage): $avgBpm")
    }
}
