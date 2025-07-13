package org.htwk.pacing.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.export.exportAllAsZip
import org.htwk.pacing.ui.components.HeartRateCard
import org.htwk.pacing.ui.components.ImportDataHealthConnect
import org.koin.androidx.compose.koinViewModel

val requiredPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class) //für schreiben des csv imports
)

/**
 * Verwaltet die Verbindung zu Health Connect.
 * Prüft beim Start und bei `ON_RESUME`, ob alle Berechtigungen vorhanden sind.
 * Bietet eine Möglichkeit, die Health Connect App zu öffnen oder Berechtigungen anzufordern.
 * Nutzt `HealthConnectItem` als UI-Eintrag.
 * Startet `HeartRateScreen` zur Anzeige der Daten.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.exportDataToZip(context, it)
            }
        }
    }

    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(40.dp)) {
            SectionTitle(stringResource(R.string.connections_and_services))

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

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.stored_data))

            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_data_to_zip_archive))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.data_protection_notice)) },
            text = {
                // TODO german: Text("Beim Export werden personenbezogene Daten gespeichert. Bitte stimme der Verarbeitung zu.")
                Text(stringResource(R.string.personalised_data_will_be_stored_by_exporting_please_consent_to_the_processing))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        launcher.launch("pacing_export.zip")
                    }) {
                    Text(stringResource(R.string.agree))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }
}

@Composable
fun SectionTitle(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(vertical = 10.dp)
)

/**
 * Zeigt Verbindungsstatus ("Connected"/"Not connected") an.
 * Button „Edit“ öffnet Health Connect oder fordert Rechte an.
 * Ruft `HeartRateScreen()` zur Anzeige von Gesundheitsdaten auf.
 */
@Composable
fun HealthConnectItem(connected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.health_connect),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (connected) stringResource(R.string.connected) else stringResource(R.string.not_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.edit))
        }

    }
    HeartRateCard()
    ImportDataHealthConnect()
}

class SettingsViewModel(
    private val db: PacingDatabase
) : ViewModel() {

    /**
     * Starts export as background thread.
     *
     * @param context The application context to retrieve the contentresolver
     * @param uri the target uri for the zip-file which is coming from ActivityResultLauncher
     */
    fun exportDataToZip(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    exportAllAsZip(db, outputStream)
                }
            } catch (e: Exception) {
                Log.e("ExportError", "Failed to export data", e)
            }
        }
    }
}
