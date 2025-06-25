package org.htwk.pacing.ui.screens

import android.content.Context
import android.content.Intent
import android.health.connect.HealthConnectManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

val requiredPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class)
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val requestPermissionsActivity = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("RequestPermissions", "Granted: $granted")
    }

    val isConnected = viewModel.isConnected.collectAsState()
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

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(
                text = "Connections and Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HealthConnectItem(
                connected = isConnected.value,
                onClick = {
                    if (!isConnected.value) {
                        requestPermissionsActivity.launch(requiredPermissions)
                        return@HealthConnectItem
                    }

                    val intent =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS)
//                .putExtra(Intent.EXTRA_PACKAGE_NAME, BuildConfig.APPLICATION_ID)
                        } else {
                            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        }
                    context.startActivity(intent)
                },
            )
        }
    }
}

class SettingsViewModel(
    private val context: Context
) : ViewModel() {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun checkPermissions() {
        viewModelScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            _isConnected.value = requiredPermissions.all { it in granted }
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
            Text(if (connected) "Edit" else "Connect")
        }
    }
}