package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datenschutzerklärung") },
        text = {
            Text(
                text = """
                    Hier kommt deine vollständige Datenschutzerklärung rein. 
                    Du kannst so viele Zeilen schreiben, wie du willst. 
                    Der Text ist scrollbar, falls er länger ist.
                    
                    Beispiel:
                    • Wir speichern persönliche Daten nur mit Einwilligung.
                    • Daten werden verschlüsselt übertragen und gespeichert.
                    • Du kannst jederzeit den Export oder die Löschung deiner Daten beantragen.
                    
                    Weitere Details hier...
                """.trimIndent(),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
