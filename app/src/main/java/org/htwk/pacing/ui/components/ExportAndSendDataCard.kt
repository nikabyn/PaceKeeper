package org.htwk.pacing.ui.components

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.htwk.pacing.backend.export.sendDatabaseToServer

@Composable
fun ExportAndSendDataCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }

    suspend fun performSend() {
        try {
            val success = sendDatabaseToServer(context)
            isSuccess = success
            statusMessage = if (success)
                "Datenbank erfolgreich versendet!"
            else
                "Fehler beim Versenden der Datenbank"
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

    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Datenbank exportieren und versenden", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sendet die komplette Datenbank an den Server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { send() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(20.dp),
                    strokeWidth = 2.dp
                )
                Text("Wird versendet...")
            } else {
                Text("Jetzt versenden")
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
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
