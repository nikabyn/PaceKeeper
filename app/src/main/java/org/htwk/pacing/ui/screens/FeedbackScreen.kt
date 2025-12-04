package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.SettingsSubScreen

@Composable
fun FeedbackScreen(
    navController: NavController,
    viewModel: UserProfileViewModel
) {
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_feedback),
        navController = navController,
        viewModel = viewModel
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Platzhalter für "+stringResource(R.string.title_settings_feedback),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
