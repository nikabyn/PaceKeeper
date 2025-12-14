package org.htwk.pacing.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.export.exportAllAsZip
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.NotificationCard
import org.htwk.pacing.ui.components.UniversalSettingsCard
import org.htwk.pacing.ui.components.UserProfileCard
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing


@Composable
fun SettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
        ) {
            UserProfileCard(navController = navController)

            Spacer(modifier = Modifier.height(Spacing.large))
            NotificationCard(navController = navController)
            Spacer(modifier = Modifier.height(Spacing.large))

            UniversalSettingsCard(
                route = Route.SERVICES,
                name = stringResource(R.string.title_settings_services),
                description = stringResource(R.string.subtitle_settings_services),
                iconRes = R.drawable.settings_services,
                navController = navController,
                style = CardStyle.shapeFirstInGroup
            )

            UniversalSettingsCard(
                route = Route.DATA,
                name = stringResource(R.string.title_settings_data),
                description = stringResource(R.string.subtitle_settings_data),
                iconRes = R.drawable.settings_data,
                navController = navController,
                style = CardStyle.shapeInGroup
            )

            UniversalSettingsCard(
                route = Route.NOTIFICATIONS,
                name = stringResource(R.string.title_settings_notifications),
                description = stringResource(R.string.subtitle_settings_notifications),
                iconRes = R.drawable.settings_notifications,
                navController = navController,
                style = CardStyle.shapeInGroup
            )

            UniversalSettingsCard(
                route = Route.APPEAREANCE,
                name = stringResource(R.string.title_settings_appearance),
                description = stringResource(R.string.subtitle_settings_appearance),
                iconRes = R.drawable.settings_appereance,
                navController = navController,
                style = CardStyle.shapeInGroup
            )

            UniversalSettingsCard(
                route = Route.INFORMATION,
                name = stringResource(R.string.title_settings_information),
                description = stringResource(R.string.subtitle_settings_information),
                iconRes = R.drawable.settings_information,
                navController = navController,
                style = CardStyle.shapeLastInGroup
            )
            Spacer(modifier = Modifier.height(Spacing.large))

            UniversalSettingsCard(
                route = Route.FEEDBACK,
                name = stringResource(R.string.title_settings_feedback),
                description = stringResource(R.string.subtitle_settings_feedback),
                icon = Icons.Filled.Settings,
                navController = navController,
                style = CardStyle.shape
            )
        }


    }
}


class SettingsViewModel(
    context: Context,
    val db: PacingDatabase
) : ViewModel() {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val _isConnected = MutableStateFlow(false)
    /*
    wird das weiter verwendet bei den Services?
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
    }*/

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