package org.htwk.pacing.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.htwk.pacing.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import org.htwk.pacing.ui.theme.PacingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PopupDemo()
                }
            }
        }
    }
}

@Composable
fun PopupDemo() {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { showDialog = true }) {
            Text("Popup öffnen")
        }

        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Hier kommt dein Popup-Inhalt hin", style = MaterialTheme.typography.titleMedium)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = { showDialog = false }) {
                            Text("Schließen")
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* nicht schließbar, daher leer */ },
        title = { Text("Zustimmung erforderlich") },
        text = { Text("Bitte stimme den Nutzungsbedingungen zu, um die App zu verwenden.") },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Zustimmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Ablehnen")
            }
        }
    )
}