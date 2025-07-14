package org.htwk.pacing.ui.components

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.launch
import org.htwk.pacing.R
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

            heartRateByTime =
                HealthConnectHelper.readHeartRateSamples(context, yesterdayEnd, now.toInstant())
            avgHeartRate =
                HealthConnectHelper.readAvgHeartRate(context, yesterdayEnd, now.toInstant())
            yesterdaySteps =
                HealthConnectHelper.readStepsCount(context, yesterdayStart, yesterdayEnd)
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

            Text(stringResource(R.string.heart_rate_24h_bpm, avgHeartRate?.toInt() ?: "-"))
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.heart_rate_samples))
            heartRateByTime.take(10).forEach { (time, bpm) ->
                Text(stringResource(R.string.bpm_at_given_time, time, bpm))
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.steps_yesterday, yesterdaySteps ?: "-"))
            Text(stringResource(R.string.steps_today, todaySteps ?: "-"))
        }
    }

    // Daten laden bei erstem Render, wenn Berechtigungen vorhanden sind
    LaunchedEffect(Unit) {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.containsAll(HealthConnectHelper.requiredPermissions)) {
            scope.launch { queryData() }
        }
    }
}
