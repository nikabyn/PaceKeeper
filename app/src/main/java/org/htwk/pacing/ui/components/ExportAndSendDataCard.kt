package org.htwk.pacing.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.export.exportAndSendData
import org.htwk.pacing.ui.screens.UserProfileViewModel
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing

@Composable
fun ExportAndSendDataCard(
    userProfileViewModel: UserProfileViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    val statusErrorSendingDatabase = stringResource(R.string.status_error_sending_database)
    val statusSuccessDatabaseSent = stringResource(R.string.status_success_database_sent)
    val statusErrorGenericSending = stringResource(R.string.status_error_generic_sending)

    suspend fun performSend() {
        try {
            val success = exportAndSendData(context, userProfileViewModel)
            isSuccess = success
            statusMessage = if (success)
                statusSuccessDatabaseSent
            else
                statusErrorSendingDatabase
        } catch (e: Exception) {
            Log.e("ExportAndSend", "Fehler beim Senden", e)
            isSuccess = false
            statusMessage = "Fehler: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun send() {
        isLoading = true
        statusMessage = ""
        isSuccess = false
        scope.launch { performSend() }
    }

    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.large),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
        ) {
            Column {
                Text(
                    stringResource(R.string.title_export_send_database),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.description_export_send_database),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { send() },
                style = PrimaryButtonStyle,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(stringResource(R.string.button_sending))
                } else {
                    Text(stringResource(R.string.button_send_now))
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
