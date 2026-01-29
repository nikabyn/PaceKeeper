package org.htwk.pacing.ui.components

import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.restartApp
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun StartEvaluationMode(
    modeViewModel: ModeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    val mode by modeViewModel.mode.collectAsState()
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
                stringResource(R.string.demo_data_button_text),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (mode?.demo == true) (
                        Text(stringResource(R.string.demo_end_button_text)))
                else
                    (Text(stringResource(R.string.demo_start_button_text)))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.demo_data_dialog_title)) },
            text = {
                Text(stringResource(R.string.evaluation_mode_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        if (mode?.demo == true) {
                            modeViewModel.setDemoMode(false)
                            Log.d("Modus", "Normalbetrieb")
                        } else {
                            modeViewModel.setDemoMode(true)
                            Log.d("Modus", "Demobetrieb")
                        }

                        restartApp(context)
                        //hardKillApp(context)

                    }
                ) {
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


