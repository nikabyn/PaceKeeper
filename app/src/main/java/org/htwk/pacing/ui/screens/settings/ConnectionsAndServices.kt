package org.htwk.pacing.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.ui.screens.HealthConnectItem
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painterResource(R.drawable.rounded_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.connections_and_services),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                )
            ) {
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
            }
        }

    }
}

class ConnectionsAndServicesViewModel(
    context: Context,
    val db: PacingDatabase
) : ViewModel() {
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