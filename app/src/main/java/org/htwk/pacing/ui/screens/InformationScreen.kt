package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.Button
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing

@Composable
fun InformationScreen(
    navController: NavController
) {
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showLicenceDialog by remember { mutableStateOf(false) }
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_information),
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            Button(
                onClick = { viewModel.openWelcomeScreen(navController) },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.welcome_screen))
            }
            Button(
                onClick = { showPrivacyPolicyDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.privacy_policy))
            }
            Button(
                onClick = { showLicenceDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.view_licence))
            }

            if (showPrivacyPolicyDialog) {
                PrivacyPolicyDialog(
                    onDismiss = { showPrivacyPolicyDialog = false }
                )
            }
            if (showLicenceDialog) {
                LicenceDialog(
                    onDismiss = { showLicenceDialog = false }
                )
            }
        }
    }
}

private fun UserProfileViewModel.openWelcomeScreen(navController: NavController) {
    viewModelScope.launch {
        val profile = dao.getProfile()?.copy(checkedIn = false)
            ?: error("User profile should always exist")
        dao.insertOrUpdate(profile)
        navController.navigate(Route.WELCOME)
    }
}
