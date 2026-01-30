package org.htwk.pacing.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.Button
import org.htwk.pacing.ui.components.DemoBanner
import org.htwk.pacing.ui.components.ImportDataHealthConnect
import org.htwk.pacing.ui.components.ImportDemoDataHealthConnect
import org.htwk.pacing.ui.components.ModeViewModel
import org.htwk.pacing.ui.components.SettingsSubScreen
import org.htwk.pacing.ui.components.StartEvaluationMode
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun DataScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    modeViewModel: ModeViewModel = koinViewModel()
) {
    SettingsSubScreen(
        title = stringResource(R.string.title_settings_data),
        navController = navController,
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->
            uri?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    settingsViewModel.exportDataToZip(context, it)
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.large)
        ) {
            ImportDataHealthConnect()
            StartEvaluationMode(modeViewModel)

            org.htwk.pacing.ui.components.ZipDataImport_import_temp(
                heartRateDao = settingsViewModel.db.heartRateDao(),
                validatedEnergyLevelDao = settingsViewModel.db.validatedEnergyLevelDao()
            )


            ImportDemoDataHealthConnect()


                Card(
                    colors = CardStyle.colors,
                    shape = CardStyle.shape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Spacing.large,
                            vertical = Spacing.largeIncreased
                        )
                    ) {
                        Text(
                            stringResource(R.string.stored_data),
                            style = MaterialTheme.typography.titleMedium
                        )

                        var showDialog by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showDialog = true },
                            style = PrimaryButtonStyle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.export_data_to_zip_archive))
                        }
                        Spacer(modifier = Modifier.height(Spacing.large))
                        if (showDialog) {
                            AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text(stringResource(R.string.data_protection_notice)) },
                                text = {
                                    Text(stringResource(R.string.personalised_data_will_be_stored_by_exporting_please_consent_to_the_processing))
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDialog = false
                                            launcher.launch("pacing_export.zip")
                                        }) {
                                        Text(stringResource(R.string.agree))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}