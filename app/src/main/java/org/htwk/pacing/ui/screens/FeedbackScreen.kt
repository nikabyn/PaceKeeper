package org.htwk.pacing.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.backend.DomainRoutes
import org.htwk.pacing.ui.components.ExportAndSendDataCard
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedbackScreen(
    navController: NavController,
    viewModel: UserProfileViewModel = koinViewModel()
) {
    val context = LocalContext.current
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_feedback),
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            ExportAndSendDataCard(userProfileViewModel = viewModel)

            Column {
                Text(
                    stringResource(R.string.title_survey),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(R.string.description_survey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val linkUri = Uri.parse(DomainRoutes.SURVEY_PACEKEEPER_URL)
                    val intent = Intent(Intent.ACTION_VIEW, linkUri)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.join_survey))
            }
        }
    }
}
