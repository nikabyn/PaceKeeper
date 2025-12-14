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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.datetime.LocalTime
import org.htwk.pacing.R
import org.htwk.pacing.ui.components.NotificationPermitCard
import org.htwk.pacing.ui.components.RestingHoursCard
import org.koin.androidx.compose.koinViewModel

/**
 * Formats a [LocalTime] object into a string in "HH:mm" format.
 */
fun LocalTime.formatTime(): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

/**
 * Parses a string in "HH:mm" format into a [LocalTime] object.
 */
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

/**
 * A Composable that displays the notification settings screen.
 *
 * @param navController The NavController for navigating back.
 * @param modifier A Modifier for this composable.
 * @param userProfileViewModel The ViewModel for the user profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    // ViewModel für den UserProfile
    userProfileViewModel: UserProfileViewModel = koinViewModel()
) {

    // Hol die UserProfile-Daten
    val profile by userProfileViewModel.profile.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    // Lokale Zustände für die Schalter
    var warningPermit by remember { mutableStateOf(profile?.warningPermit ?: false) }
    var reminderPermit by remember { mutableStateOf(profile?.reminderPermit ?: false) }
    var suggestionPermit by remember { mutableStateOf(profile?.suggestionPermit ?: false) }

    // Wenn sich das Profil ändert, aktualisiere die lokalen Zustände
    LaunchedEffect(profile) {
        profile?.let {
            warningPermit = it.warningPermit
            reminderPermit = it.reminderPermit
            suggestionPermit = it.suggestionPermit
        }
    }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notifications)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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

            NotificationPermitCard(
                warningPermit = warningPermit,
                reminderPermit = reminderPermit,
                suggestionPermit = suggestionPermit,

                onWarningChange = { enabled ->
                    warningPermit = enabled
                    profile?.let {
                        userProfileViewModel.saveProfile(it.copy(warningPermit = enabled))
                    }
                },

                onReminderChange = { enabled ->
                    reminderPermit = enabled
                    profile?.let {
                        userProfileViewModel.saveProfile(it.copy(reminderPermit = enabled))
                    }
                },
                onSuggestionChange = { enabled ->
                    suggestionPermit = enabled
                    profile?.let {
                        userProfileViewModel.saveProfile(it.copy(suggestionPermit = enabled))
                    }
                },

                )

            Spacer(modifier = Modifier.padding(top = 20.dp))

            profile?.let {
                RestingHoursCard(
                    restingStart = profile?.restingStart?.formatTime() ?: "22:00",
                    restingEnd = profile?.restingEnd?.formatTime() ?: "06:00",
                    onEditClick = { showDialog = true }
                )
            } ?: run {
                // Fallback
                RestingHoursCard(
                    restingStart = "22:00",
                    restingEnd = "06:00",
                    onEditClick = { showDialog = true }
                )
            }
        }
    }

    // dialog for editing personal resting hours
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

/**
 * A Composable that displays a dialog for editing resting hours.
 *
 * @param currentStart The current start time of the resting period.
 * @param currentEnd The current end time of the resting period.
 * @param onDismiss A lambda to be invoked when the dialog is dismissed.
 * @param onConfirm A lambda to be invoked when the confirm button is clicked.
 */
@Composable
fun RestingHoursDialog(
    currentStart: String,
    currentEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var start by remember { mutableStateOf(currentStart) }
    var end by remember { mutableStateOf(currentEnd) }

    // input validation
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
        title = { Text(stringResource(id = R.string.edit_resting_hours)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text(stringResource(R.string.start_time_label)) },
                    singleLine = true,
                    isError = !isStartValid && start.isNotEmpty(),
                    supportingText = {
                        if (!isStartValid && start.isNotEmpty()) {
                            Text(stringResource(R.string.time_format_helper))
                        }
                    }
                )

                Spacer(modifier = Modifier.padding(top = 12.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text(stringResource(R.string.end_time_label)) },
                    singleLine = true,
                    isError = !isEndValid && end.isNotEmpty(),
                    supportingText = {
                        if (!isEndValid && end.isNotEmpty()) {
                            Text(stringResource(R.string.time_format_helper))
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
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
