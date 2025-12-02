package org.htwk.pacing.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.health_connect.wantedPermissions
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.export.exportAllAsZip
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.Button
import org.htwk.pacing.ui.components.ExportAndSendDataCard
import org.htwk.pacing.ui.components.HeartRateCard
import org.htwk.pacing.ui.components.ImportDataHealthConnect
import org.htwk.pacing.ui.components.ImportDemoDataHealthConnect
import org.htwk.pacing.ui.components.UniversalSettingsCard
import org.htwk.pacing.ui.components.UserProfileCard
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import org.htwk.pacing.ui.theme.TonalButtonStyle
import org.koin.androidx.compose.koinViewModel


/**
 * Verwaltet die Verbindung zu Health Connect.
 * Prüft beim Start und bei `ON_RESUME`, ob alle Berechtigungen vorhanden sind.
 * Bietet eine Möglichkeit, die Health Connect App zu öffnen oder Berechtigungen anzufordern.
 * Nutzt `HealthConnectItem` als UI-Eintrag.
 * Startet `HeartRateScreen` zur Anzeige der Daten.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    userProfileViewModel: UserProfileViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val isConnected by viewModel.isConnected.collectAsState()

    viewModel.checkPermissions()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestPermissionsActivity = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("RequestPermissions", "Granted: $granted")
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
        Column(
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
        ) {
            SectionTitle(stringResource(R.string.connections_and_services))

            HealthConnectItem(
                connected = isConnected,
                db = viewModel.db,
                onClick = {
                    if (!isConnected) {
                        requestPermissionsActivity.launch(wantedPermissions)
                        return@HealthConnectItem
                    }

                    val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    context.startActivity(intent)
                },
            )
            Spacer(modifier = Modifier.height(Spacing.large))

            HeartRateCard()
            Spacer(modifier = Modifier.height(Spacing.large))

            ImportDataHealthConnect()
            Spacer(modifier = Modifier.height(Spacing.large))

            ImportDemoDataHealthConnect()
            Spacer(modifier = Modifier.height(Spacing.large))

            SectionTitle(stringResource(R.string.stored_data))
            Button(
                onClick = { showDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_data_to_zip_archive))
            }
            Spacer(modifier = Modifier.height(Spacing.large))

            UserProfileCard(navController = navController)
            Spacer(modifier = Modifier.height(Spacing.large))


            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_services),
                description = stringResource(R.string.subtitle_settings_services),
                iconRes = R.drawable.settings_services,
                navController = navController
            )

            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_data),
                description = stringResource(R.string.subtitle_settings_data),
                iconRes = R.drawable.settings_data,
                navController = navController
            )

            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_notifications),
                description = stringResource(R.string.subtitle_settings_notifications),
                iconRes = R.drawable.settings_notifications,
                navController = navController
            )

            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_appereance),
                description = stringResource(R.string.subtitle_settings_appereance),
                iconRes = R.drawable.settings_appereance,
                navController = navController
            )

            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_information),
                description = stringResource(R.string.subtitle_settings_information),
                iconRes = R.drawable.settings_information,
                navController = navController
            )



            UniversalSettingsCard(
                route = Route.SERVICES,
                name = "Services",
                description = "Connections",
                icon = Icons.Filled.Settings,
                navController = navController
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            // entweder Material Icon oder eigenes Drawable Icon
            UniversalSettingsCard(
                route = Route.MEASUREMENTS,
                name = stringResource(R.string.title_user_profile),
                description = stringResource(R.string.icon_profile_description),
                iconRes = R.drawable.rounded_show_chart_24,
                //oder icon = Icons.Filled.Settings,
                navController = navController
            )
            Spacer(modifier = Modifier.height(Spacing.large))

            ExportAndSendDataCard(userProfileViewModel = userProfileViewModel)
        }

    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.data_protection_notice)) },
            text = {
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
            }
        )
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
fun HealthConnectItem(connected: Boolean, db: PacingDatabase, onClick: () -> Unit) {
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
        Button(onClick = onClick, style = TonalButtonStyle) {
            Text(stringResource(R.string.edit))
        }
    }
}

class SettingsViewModel(
    context: Context,
    val db: PacingDatabase
) : ViewModel() {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun checkPermissions() {
        viewModelScope.launch {
            try {
                val granted = client.permissionController.getGrantedPermissions()
                _isConnected.value = wantedPermissions.any { it in granted }
            } catch (_: Exception) {
                Log.e("SettingsViewModel", "Failed to get granted permissions.")
            }
        }
    }

    /**
     * Starts export as background thread.
     *
     * @param context The application context to retrieve the contentresolver
     * @param uri the target uri for the zip-file which is coming from ActivityResultLauncher
     */
    fun exportDataToZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        exportAllAsZip(db, outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("ExportError", "Failed to export data", e)
            }
        }
    }
}