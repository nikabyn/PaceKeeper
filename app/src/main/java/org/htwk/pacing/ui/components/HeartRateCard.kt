package org.htwk.pacing.ui.components

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Hilfsklasse zur Kapselung von Health Connect-Funktionen.
 * Vermeidet Duplikation beim Abruf und Aggregation von Daten.
 */
object HealthConnectHelper {

    suspend fun readAvgHeartRate(client: HealthConnectClient, from: Instant, to: Instant): Double? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        return result[HeartRateRecord.BPM_AVG]?.toDouble()
    }

    suspend fun readHeartRateSamples(client: HealthConnectClient, from: Instant, to: Instant): List<Pair<String, Long>> {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        return records.records.flatMap { record ->
            record.samples.map {
                val t = record.startTime.atZone(ZoneId.systemDefault()).toLocalTime().toString()
                t to it.beatsPerMinute
            }
        }
    }

    suspend fun readStepsCount(client: HealthConnectClient, from: Instant, to: Instant): Long? {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        return result[StepsRecord.COUNT_TOTAL]
    }

    suspend fun readTodaySteps(client: HealthConnectClient, from: Instant, to: Instant): Long {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to)
            )
        )
        return records.records.sumOf { it.count }
    }
}

/**
 * Zeigt:
 * ∅ Herzfrequenz der letzten 24h
 * Letzte Herzfrequenz-Messwerte (Zeit + bpm)
 * Schrittanzahl gestern & heute
 * Lädt Daten nur, wenn Berechtigungen vorhanden sind.
 * Verpackt UI in `Box` mit Border und abgerundeten Ecken.
 */
@Composable
fun HeartRateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    var avgHeartRate by remember { mutableStateOf<Double?>(null) }
    var heartRateByTime by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var yesterdaySteps by remember { mutableStateOf<Long?>(null) }
    var todaySteps by remember { mutableStateOf<Long?>(null) }

    /**
     * Lädt Herzfrequenz- und Schrittzahlen-Daten der letzten 24h.
     * Führt sowohl Aggregationen als auch Einzelabfragen aus.
     * Nur aufrufbar, wenn Health Connect-Berechtigungen erteilt wurden.
     */
    suspend fun queryData() {
        scope.launch(Dispatchers.IO) {
            try {
                val client = HealthConnectClient.getOrCreate(context)

                val now = ZonedDateTime.now()
                val zone = now.zone
                val todayStart = now.toLocalDate().atStartOfDay(zone).toInstant()
                val yesterdayStart = now.minusDays(1).toLocalDate().atStartOfDay(zone).toInstant()
                val yesterdayEnd = todayStart

                heartRateByTime = HealthConnectHelper.readHeartRateSamples(client, yesterdayEnd, now.toInstant())
                avgHeartRate = HealthConnectHelper.readAvgHeartRate(client, yesterdayEnd, now.toInstant())
                yesterdaySteps = HealthConnectHelper.readStepsCount(client, yesterdayStart, yesterdayEnd)
                todaySteps = HealthConnectHelper.readTodaySteps(client, todayStart, now.toInstant())

            } catch (e: Exception) {
                Log.e("HeartRateScreen", "Fehler beim Lesen von Daten", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text("⌀ Herzfrequenz (24h): ${avgHeartRate?.toInt() ?: "-"} bpm")
            Spacer(Modifier.height(8.dp))

            Text("Herzfrequenz-Samples:")
            heartRateByTime.take(10).forEach { (time, bpm) ->
                Text(" - $time: $bpm bpm")
            }

            Spacer(Modifier.height(16.dp))
            Text("Schritte gestern: ${yesterdaySteps ?: "-"}")
            Text("Schritte heute: ${todaySteps ?: "-"}")
        }
    }

    // Triggert Datenabfrage beim ersten Render, wenn Berechtigungen vorhanden sind.
    LaunchedEffect(Unit) {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            queryData()
        }
    }
}
