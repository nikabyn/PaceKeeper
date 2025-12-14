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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.health_connect.wantedPermissions
import org.htwk.pacing.ui.Route
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
    val isHealthConnectConnected by viewModel.isHealthConnectConnected.collectAsState()
    val isFitbitConnected by viewModel.isFitbitConnected.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshConnectedStatus()
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
                tip = if (isHealthConnectConnected) {
                    stringResource(R.string.connected)
                } else {
                    stringResource(R.string.missing_permissions)
                },
                enabled = true,
                iconId = R.drawable.health_connect,
                onClick = {
                    if (isHealthConnectConnected) {
                        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        context.startActivity(intent)
                    } else {
                        requestPermissionsActivity.launch(wantedPermissions)
                    }
                },
            )
            ConnectionCard(
                name = stringResource(R.string.fitbit),
                tip = if (isFitbitConnected) {
                    stringResource(R.string.connected)
                } else {
                    stringResource(R.string.login_with_fitbit)
                },
                enabled = true,
                iconId = R.drawable.fitbit,
                onClick = { navController.navigate(Route.FITBIT) },
            )
        }
    }
}

@Composable
fun ConnectionCard(
    name: String,
    tip: String,
    enabled: Boolean,
    @DrawableRes iconId: Int,
    onClick: () -> Unit,
) {
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        onClick = onClick,
        enabled = enabled,
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
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = tip,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                painterResource(R.drawable.rounded_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

class ConnectionsAndServicesViewModel(
    context: Context,
    fitbitViewModel: FitbitViewModel
) : ViewModel() {
    private companion object {
        const val TAG = "ConnectionsAndServicesViewModel"
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val refreshTrigger = MutableStateFlow(Unit)

    fun refreshConnectedStatus() {
        viewModelScope.launch { refreshTrigger.emit(Unit) }
    }

    val isHealthConnectConnected = refreshTrigger.map {
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get granted permissions: $e")
            return@map false
        }
        wantedPermissions.any { it in granted }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        false
    )

    val isFitbitConnected = fitbitViewModel.isFitbitConnected
}