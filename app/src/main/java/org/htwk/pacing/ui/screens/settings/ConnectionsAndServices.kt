package org.htwk.pacing.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import org.htwk.pacing.backend.data_collection.fitbit.OAuth2Provider
import org.htwk.pacing.backend.data_collection.health_connect.wantedPermissions
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.UserProfileEntry
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectionsAndServicesScreen(
    navController: NavController,
    fitbitOauthUri: Uri? = null,
    viewModel: ConnectionsAndServicesViewModel = koinViewModel(),
) {
    if (fitbitOauthUri != null) {
        viewModel.onFitbitOauthRedirect(fitbitOauthUri)
    }

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
                tip = "Request permissions to connect",
                connected = isHealthConnectConnected,
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
                tip = "Login with Fitbit Account",
                connected = isFitbitConnected,
                iconId = R.drawable.fitbit,
                onClick = { viewModel.openFitbitLogin() },
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

class ConnectionsAndServicesViewModel(
    private val context: Context,
    private val db: PacingDatabase,
    private val fitbitOAuth: OAuth2Provider,
) : ViewModel() {
    companion object {
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
        } catch (_: Exception) {
            Log.e("SettingsViewModel", "Failed to get granted permissions.")
            return@map false
        }
        wantedPermissions.any { it in granted }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        false
    )

    fun openFitbitLogin() {
        val scopes = arrayOf(
            "activity",
            "cardio_fitness",
            "electrocardiogram",
            "heartrate",
            "irregular_rhythm_notifications",
            "location",
            "nutrition",
            "oxygen_saturation",
            "respiratory_rate",
            "sleep",
            "temperature",
            "weight",
        )

        fitbitOAuth.openLogin(context, scopes.toList())
    }

    fun onFitbitOauthRedirect(uri: Uri) {
        Log.d("ConnectionsAndServicesViewModel", "Received OAuth redirect = $uri")

        viewModelScope.launch {
            val tokens = try {
                fitbitOAuth.onLoginResult(uri)
            } catch (e: Throwable) {
                Log.e(TAG, "Caught error while trying to request OAuth access token: $e")
                return@launch
            }
            Log.d(TAG, "tokens = $tokens")

            // TODO: Properly save tokens into user profile

            val newProfile = db.userProfileDao()
                .getCurrentProfileDirect()
                ?.copy(fitbitOauthToken = tokens.accessToken)
                ?: UserProfileEntry.createInitial()
            db.userProfileDao().insertOrUpdate(newProfile)
        }
    }

    val isFitbitConnected = db.userProfileDao()
        .getCurrentProfile()
        .map { userProfile ->
            // TODO: ping fitbit to check whether token is still valid
            userProfile?.fitbitOauthToken != null
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            false
        )
}