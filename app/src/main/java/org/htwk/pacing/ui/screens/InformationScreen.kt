package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.Button
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.PrimaryButtonStyle

@Composable
fun InformationScreen(
    navController: NavController,
    viewModel: UserProfileViewModel
) {
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_information),
        navController = navController,
        viewModel = viewModel
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { showPrivacyPolicyDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    (stringResource(R.string.privacy_policy)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (showPrivacyPolicyDialog) {
                PrivacyPolicyDialog(
                    onDismiss = { showPrivacyPolicyDialog = false }
                )
            }
        }
    }
}
