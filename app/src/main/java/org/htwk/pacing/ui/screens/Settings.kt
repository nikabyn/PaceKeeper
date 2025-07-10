package org.htwk.pacing.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.export.exportAllAsZip
import org.htwk.pacing.ui.components.HeartRateCard

val requiredPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class)
)

/**
 * Verwaltet die Verbindung zu Health Connect.
 * Prüft beim Start und bei `ON_RESUME`, ob alle Berechtigungen vorhanden sind.
 * Bietet eine Möglichkeit, die Health Connect App zu öffnen oder Berechtigungen anzufordern.
 * Nutzt `HealthConnectItem` als UI-Eintrag.
 * Startet `HeartRateScreen` zur Anzeige der Daten.
 */
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

    // Prüft, ob alle notwendigen Health Connect Berechtigungen vorhanden sind.
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
                    val launchIntent =
                        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
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

    // CsvExportManager mit Kontext initialisieren
    val db = remember { PacingDatabase.getInstance(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.let { stream ->
                CoroutineScope(Dispatchers.IO).launch {
                    exportAllAsZip(db, stream)
                }
            }
        }
    }

    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showDialog = true }) {
            Text("Daten exportieren")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Datenschutz-Hinweis") },
            text = {
                Text("Beim Export werden personenbezogene Daten gespeichert. Bitte stimme der Verarbeitung zu.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        launcher.launch("pacing_export.zip")
                    }
                ) {
                    Text("Zustimmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Zeigt Verbindungsstatus ("Connected"/"Not connected") an.
 * Button „Edit“ öffnet Health Connect oder fordert Rechte an.
 * Ruft `HeartRateScreen()` zur Anzeige von Gesundheitsdaten auf.
 */
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
}
