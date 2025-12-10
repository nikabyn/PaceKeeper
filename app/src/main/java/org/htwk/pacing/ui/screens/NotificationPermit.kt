package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.datetime.LocalTime
import org.htwk.pacing.ui.components.NotificationPermitCard
import org.htwk.pacing.ui.components.RestingHoursCard
import org.koin.androidx.compose.koinViewModel


fun LocalTime.formatTime(): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}


fun String.parseTime(): LocalTime? {
    return try {
        val parts = this.split(":")
        if (parts.size == 2) {
            LocalTime(parts[0].toInt(), parts[1].toInt())
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    // ViewModel für den UserProfile
    userProfileViewModel: UserProfileViewModel = koinViewModel()
) {

    // Hol die UserProfile-Daten
    val profile by userProfileViewModel.profile.collectAsState()

    var showDialog by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Benachrichtigungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // Verwende die neue NotificationPermitCard mit den UserProfile-Daten
            NotificationPermitCard(
                reminderPermit = profile?.reminderPermit ?: false,
                suggestionPermit = profile?.suggestionPermit ?: false,
                onReminderChange = { enabled ->
                    profile?.let {
                        userProfileViewModel.saveProfile(
                            it.copy(reminderPermit = enabled)
                        )
                    }
                },
                onSuggestionChange = { enabled ->
                    profile?.let {
                        userProfileViewModel.saveProfile(
                            it.copy(suggestionPermit = enabled)
                        )
                    }
                },
                onRestingTimeClick = { showDialog = true }
            )

            Spacer(modifier = Modifier.padding(top = 20.dp))

            // RestingHoursCard mit LocalTime anzeigen
            profile?.let {
                RestingHoursCard(
                    restingStart = profile?.restingStart?.formatTime() ?: "22:00",
                    restingEnd = profile?.restingEnd?.formatTime() ?: "06:00",
                    onEditClick = { showDialog = true }
                )
            } ?: run {
                // Fallback, wenn noch kein Profil existiert
                RestingHoursCard(
                    restingStart = "22:00",
                    restingEnd = "06:00",
                    onEditClick = { showDialog = true }
                )
            }
        }
    }

    // Dialog zum Bearbeiten der Ruhezeit
    if (showDialog) {
        val currentStart = profile?.restingStart ?: LocalTime(22, 0)
        val currentEnd = profile?.restingEnd ?: LocalTime(6, 0)

        RestingHoursDialog(
            currentStart = profile?.restingStart?.formatTime() ?: "22:00",
            currentEnd = profile?.restingEnd?.formatTime() ?: "06:00",
            onDismiss = { showDialog = false },
            onConfirm = { newStartStr, newEndStr ->
                // Verwende parseTime()
                val newStart = newStartStr.parseTime() ?: LocalTime(22, 0)
                val newEnd = newEndStr.parseTime() ?: LocalTime(6, 0)

                profile?.let {
                    userProfileViewModel.saveProfile(
                        it.copy(
                            restingStart = newStart,
                            restingEnd = newEnd
                        )
                    )
                }
                showDialog = false
            }
        )
    }
}

@Composable
fun RestingHoursDialog(
    currentStart: String,
    currentEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var start by remember { mutableStateOf(currentStart) }
    var end by remember { mutableStateOf(currentEnd) }

    // Validierung der Eingabe
    fun isValidTimeFormat(time: String): Boolean {
        return try {
            val parts = time.split(":")
            if (parts.size != 2) return false
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            hour in 0..23 && minute in 0..59
        } catch (e: Exception) {
            false
        }
    }

    val isStartValid = isValidTimeFormat(start)
    val isEndValid = isValidTimeFormat(end)
    val canSave = isStartValid && isEndValid

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ruhezeiten bearbeiten") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Startzeit (HH:MM)") },
                    singleLine = true,
                    isError = !isStartValid && start.isNotEmpty(),
                    supportingText = {
                        if (!isStartValid && start.isNotEmpty()) {
                            Text("Format: 00:00 bis 23:59")
                        }
                    }
                )

                Spacer(modifier = Modifier.padding(top = 12.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("Endzeit (HH:MM)") },
                    singleLine = true,
                    isError = !isEndValid && end.isNotEmpty(),
                    supportingText = {
                        if (!isEndValid && end.isNotEmpty()) {
                            Text("Format: 00:00 bis 23:59")
                        }
                    }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(start, end) },
                enabled = canSave
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

