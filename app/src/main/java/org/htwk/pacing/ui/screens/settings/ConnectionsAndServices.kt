package org.htwk.pacing.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.health_connect.wantedPermissions
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectionsAndServicesScreen(
    navController: NavController,
    viewModel: ConnectionsAndServicesViewModel = koinViewModel(),
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

    SettingsSubScreen(
        title = stringResource(R.string.connections_and_services),
        navController = navController,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.large,
                vertical = Spacing.extraLarge
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
            ConnectionCard(
                name = stringResource(R.string.health_connect),
                tip = "Request permissions to connect",
                connected = isConnected,
                iconId = R.drawable.health_connect,
                onClick = {
                    if (isConnected) {
                        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        context.startActivity(intent)
                    } else {
                        requestPermissionsActivity.launch(wantedPermissions)
                    }
                },
            )
            ConnectionCard(
                name = stringResource(R.string.fitbit),
                tip = "Login with Fitbit Account",
                connected = false,
                iconId = R.drawable.fitbit,
                onClick = {},
            )
        }
    }
}

@Composable
fun ConnectionCard(
    name: String,
    tip: String,
    @DrawableRes iconId: Int,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.largeIncreased,
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(iconId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = stringResource(
                            if (connected) R.string.connected else R.string.not_connected
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = tip,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

class ConnectionsAndServicesViewModel(context: Context) : ViewModel() {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val _isConnected = MutableStateFlow(false)
    internal val isConnected: StateFlow<Boolean> = _isConnected

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
}