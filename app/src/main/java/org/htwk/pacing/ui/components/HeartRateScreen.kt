package org.htwk.pacing.ui.components

import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

fun isHealthConnectInstalled(context: android.content.Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

@Composable
fun HeartRateScreen() {
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

    fun queryData() {
        scope.launch(Dispatchers.IO) {
            try {
                val client = HealthConnectClient.getOrCreate(context)

                val now = ZonedDateTime.now()
                val todayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant()
                val yesterdayStart = now.minusDays(1).toLocalDate().atStartOfDay(now.zone).toInstant()
                val yesterdayEnd = todayStart

                val heartResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(yesterdayEnd, now.toInstant())
                    )
                )
                heartRateByTime = heartResponse.records.flatMap { record ->
                    record.samples.map {
                        val t = record.startTime.atZone(ZoneId.systemDefault()).toLocalTime().toString()
                        t to it.beatsPerMinute
                    }
                }

                val heartAgg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(HeartRateRecord.BPM_AVG),
                        timeRangeFilter = TimeRangeFilter.between(yesterdayEnd, now.toInstant())
                    )
                )
                avgHeartRate = heartAgg[HeartRateRecord.BPM_AVG]?.toDouble()

                val stepsYesterdayAgg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(yesterdayStart, yesterdayEnd)
                    )
                )
                yesterdaySteps = stepsYesterdayAgg[StepsRecord.COUNT_TOTAL]?.toLong()

                val stepsTodayAgg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(todayStart, now.toInstant())
                    )
                )
                todaySteps = stepsTodayAgg[StepsRecord.COUNT_TOTAL]?.toLong()
            } catch (e: Exception) {
                Log.e("HeartRateScreen", "Fehler beim Lesen von Daten", e)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            queryData()
        } else {
            Toast.makeText(
                context,
                "Health Connect Berechtigung nicht erteilt",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
            if (isHealthConnectInstalled(context)) {
                permissionLauncher.launch(permissions)
            } else {
                Toast.makeText(
                    context,
                    "Health Connect ist nicht installiert!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }) {
            Text("Datenzugriff anfragen")
        }

        Spacer(Modifier.height(24.dp))

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

    LaunchedEffect(Unit) {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) {
            queryData()
        }
    }
}
