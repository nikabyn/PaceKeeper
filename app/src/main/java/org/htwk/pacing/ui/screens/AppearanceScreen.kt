package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.theme.Spacing

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO
}

@Composable
fun AppearanceScreen(
    navController: NavController,
    viewModel: UserProfileViewModel
) {
    val profile by viewModel.profile.collectAsState()
    val currentThemeMode = profile?.themeMode ?: "AUTO"

    // Map database string to ThemeMode enum
    val selectedTheme = when (currentThemeMode) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK" -> ThemeMode.DARK
        else -> ThemeMode.AUTO
    }

    SettingsSubScreen(
        title = stringResource(R.string.title_settings_appearance),
        navController = navController,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedTheme == ThemeMode.LIGHT),
                        onClick = {
                            viewModel.updateThemeMode("LIGHT")
                        },
                        role = Role.RadioButton
                    )
                    .padding(vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedTheme == ThemeMode.LIGHT),
                    onClick = null
                )
                Text(
                    text = stringResource(R.string.settings_appearance_light),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.medium)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedTheme == ThemeMode.DARK),
                        onClick = {
                            viewModel.updateThemeMode("DARK")
                        },
                        role = Role.RadioButton
                    )
                    .padding(vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedTheme == ThemeMode.DARK),
                    onClick = null
                )
                Text(
                    text = stringResource(R.string.settings_appearance_dark),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.medium)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedTheme == ThemeMode.AUTO),
                        onClick = {
                            viewModel.updateThemeMode("AUTO")
                        },
                        role = Role.RadioButton
                    )
                    .padding(vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedTheme == ThemeMode.AUTO),
                    onClick = null
                )
                Text(
                    text = stringResource(R.string.settings_appearance_auto),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.medium)
                )
            }
        }
    }
}
