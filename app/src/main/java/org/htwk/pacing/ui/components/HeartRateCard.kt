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
import kotlinx.coroutines.launch
import org.htwk.pacing.backend.data_collection.HealthConnectHelper
import java.time.ZonedDateTime

/**
 * UI-Komponente zur Anzeige:
 * - Ø Herzfrequenz der letzten 24h
 * - Einzelne Herzfrequenzwerte (Zeit + bpm)
 * - Schritte gestern und heute
 * Lädt Daten nur bei vorhandener Berechtigung.
 */
@Composable
fun HeartRateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var avgHeartRate by remember { mutableStateOf<Double?>(null) }
    var heartRateByTime by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var yesterdaySteps by remember { mutableStateOf<Long?>(null) }
    var todaySteps by remember { mutableStateOf<Long?>(null) }

    suspend fun queryData() {
        try {
            val now = ZonedDateTime.now()
            val zone = now.zone
            val todayStart = now.toLocalDate().atStartOfDay(zone).toInstant()
            val yesterdayStart = now.minusDays(1).toLocalDate().atStartOfDay(zone).toInstant()
            val yesterdayEnd = todayStart

            heartRateByTime = HealthConnectHelper.readHeartRateSamples(context, yesterdayEnd, now.toInstant())
            avgHeartRate = HealthConnectHelper.readAvgHeartRate(context, yesterdayEnd, now.toInstant())
            yesterdaySteps = HealthConnectHelper.readStepsCount(context, yesterdayStart, yesterdayEnd)
            todaySteps = HealthConnectHelper.readStepsSum(context, todayStart, now.toInstant())

        } catch (e: Exception) {
            Log.e("HeartRateCard", "Fehler beim Abrufen der Daten", e)
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

    // Daten laden bei erstem Render, wenn Berechtigungen vorhanden sind
    LaunchedEffect(Unit) {
        val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(HealthConnectHelper.requiredPermissions)) {
            scope.launch { queryData() }
        }
    }
}
