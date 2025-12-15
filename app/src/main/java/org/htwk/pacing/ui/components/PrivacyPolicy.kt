package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.htwk.pacing.R

@Composable
fun loadPrivacyPolicyText(): String {
    val context = LocalContext.current

    return remember {
        context.resources.openRawResource(R.raw.privacy_policy)
            .bufferedReader()
            .use { it.readText() }
    }
}

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val privacyText = loadPrivacyPolicyText()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("privacy_dialog"),
        title = {
            Text(
                (stringResource(R.string.privacy_policy)),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = privacyText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("privacy_close")) {
                Text((stringResource(R.string.back)))
            }
        }
    )
}