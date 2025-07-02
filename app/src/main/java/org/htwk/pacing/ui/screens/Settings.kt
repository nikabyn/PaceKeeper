package org.htwk.pacing.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import org.htwk.pacing.ui.components.HeartRateCard
import org.htwk.pacing.ui.components.ImportDataHealthConnect

val requiredPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class) //für schreiben des csv imports
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalContext.current as LifecycleOwner
    var isConnected by remember { mutableStateOf(false) }

    val requestPermissionsActivity = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("HealthConnectDebug", "Permission activity result: $granted")
    }

    suspend fun updateConnectionState() {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        isConnected = requiredPermissions.all { it in granted }
    }

    LaunchedEffect(Unit) {
        updateConnectionState()
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                CoroutineScope(Dispatchers.Main).launch {
                    updateConnectionState()
                }
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(
                text = "Connections and Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HealthConnectItem(
                connected = isConnected,
                onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        Log.w("HealthConnectDebug", "Health Connect not installed.")
                    }

                    if (!isConnected) {
                        requestPermissionsActivity.launch(requiredPermissions)
                    }
                },
            )
        }
    }
}

@Composable
fun HealthConnectItem(connected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Health Connect", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (connected) "Connected" else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) {
            Text("Edit")
        }

    }
    HeartRateCard()
    ImportDataHealthConnect()
}


