package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.Spacing

@Composable
fun NotificationsScreen(navController: NavController) {
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_notifications),
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            Text(
                "Platzhalter",
                style = MaterialTheme.typography.titleMedium
            )

        }
    }
}
