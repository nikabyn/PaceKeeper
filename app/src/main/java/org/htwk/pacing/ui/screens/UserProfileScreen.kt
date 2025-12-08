package org.htwk.pacing.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.backend.database.UserProfileEntry
import org.htwk.pacing.ui.Route

/**
 * Defines the Android user profile editing screen built with Jetpack Compose.
 * It handles the display, modification, and persistence of the user's personal
 * and health-related data, including metrics essential for the pacing system.
 */
@Composable
fun UserProfileScreen(
    navController: NavController,
    viewModel: UserProfileViewModel
) {
    val profileState by viewModel.profile.collectAsState()
    val profile = profileState ?: return

    var nickname by remember { mutableStateOf(profile.nickname ?: "") }
    var birthYear by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var heightCm by remember { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var weightKg by remember { mutableStateOf(profile.weightKg?.toString() ?: "") }
    var restingHeartRateBpm by remember {
        mutableStateOf(
            profile.restingHeartRateBpm?.toString() ?: ""
        )
    }
    var selectedSex by remember { mutableStateOf(profile.sex) }
    var selectedAmputationLevel by remember { mutableStateOf(profile.amputationLevel) }
    var selectedDiagnosis by remember { mutableStateOf(profile.diagnosis) }
    var fatigueSensitivity by remember {
        mutableStateOf(
            profile.fatigueSensitivity?.toString() ?: ""
        )
    }
    var activityBaseline by remember { mutableStateOf(profile.activityBaseline?.toString() ?: "") }
    var anaerobicThreshold by remember {
        mutableStateOf(
            profile.anaerobicThreshold?.toString() ?: ""
        )
    }
    var bellScale by remember { mutableStateOf(profile.bellScale?.toString() ?: "") }
    var fitnessTracker by remember { mutableStateOf(profile.fitnessTracker ?: "") }

    // States für Dialog und ob Änderungen vorliegen
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // 1. Funktion zur Prüfung auf Änderungen
    val hasUnsavedChanges by remember(
        nickname,
        birthYear,
        heightCm,
        weightKg,
        restingHeartRateBpm,
        selectedSex,
        selectedAmputationLevel,
        selectedDiagnosis,
        fatigueSensitivity,
        activityBaseline,
        anaerobicThreshold,
        bellScale,
        fitnessTracker
    ) {
        val currentProfile = profile.copy(
            nickname = nickname.takeIf { it.isNotBlank() },
            birthYear = birthYear.toIntOrNull(),
            heightCm = heightCm.toIntOrNull(),
            weightKg = weightKg.toIntOrNull(),
            restingHeartRateBpm = restingHeartRateBpm.toIntOrNull(),
            sex = selectedSex,
            amputationLevel = selectedAmputationLevel,
            diagnosis = selectedDiagnosis,
            fatigueSensitivity = fatigueSensitivity.toIntOrNull(),
            activityBaseline = activityBaseline.toIntOrNull(),
            anaerobicThreshold = anaerobicThreshold.toIntOrNull(),
            bellScale = bellScale.toIntOrNull(),
            fitnessTracker = fitnessTracker.takeIf { it.isNotBlank() }
        )
        mutableStateOf(currentProfile != profile)
    }

    BackHandler(enabled = true) {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog = true
        } else {
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (hasUnsavedChanges) {
                        showUnsavedChangesDialog = true
                    } else {
                        navController.popBackStack()
                    }
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.label_back)
                )
            }
            Text(
                stringResource(R.string.title_user_profile),
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text(stringResource(R.string.label_nickname)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = birthYear,
            onValueChange = { birthYear = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.label_birth_year)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = heightCm,
            onValueChange = { heightCm = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_height_cm)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = weightKg,
            onValueChange = { weightKg = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_weight_kg)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = restingHeartRateBpm,
            onValueChange = { restingHeartRateBpm = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_restingHeartRateBpm)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenuField(
            label = stringResource(R.string.label_sex),
            options = UserProfileEntry.Sex.entries.map { it.name },
            selectedOption = selectedSex.name,
            onOptionSelected = { selectedSex = UserProfileEntry.Sex.valueOf(it) }
        )

        DropdownMenuField(
            label = stringResource(R.string.label_amputation_level),
            options = UserProfileEntry.AmputationLevel.entries.map { it.name },
            selectedOption = selectedAmputationLevel?.name ?: "NONE",
            onOptionSelected = {
                selectedAmputationLevel = UserProfileEntry.AmputationLevel.valueOf(it)
            }
        )

        DropdownMenuField(
            label = stringResource(R.string.label_diagnosis),
            options = listOf("Keine") + UserProfileEntry.Diagnosis.entries.map { it.name },
            selectedOption = selectedDiagnosis?.name ?: "Keine",
            onOptionSelected = {
                selectedDiagnosis =
                    if (it == "Keine") null else UserProfileEntry.Diagnosis.valueOf(it)
            }
        )

        OutlinedTextField(
            value = fatigueSensitivity,
            onValueChange = { fatigueSensitivity = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_fatigue_sensitivity)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = activityBaseline,
            onValueChange = { activityBaseline = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_activity_baseline)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = anaerobicThreshold,
            onValueChange = { anaerobicThreshold = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_anaerobic_threshold)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = bellScale,
            onValueChange = { bellScale = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.label_bell_scale)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = fitnessTracker,
            onValueChange = { fitnessTracker = it },
            label = { Text(stringResource(R.string.label_fitness_tracker)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                val updatedProfile = profile.copy(
                    nickname = nickname.takeIf { it.isNotBlank() },
                    birthYear = birthYear.toIntOrNull(),
                    heightCm = heightCm.toIntOrNull(),
                    weightKg = weightKg.toIntOrNull(),
                    restingHeartRateBpm = restingHeartRateBpm.toIntOrNull(),
                    sex = selectedSex,
                    amputationLevel = selectedAmputationLevel,
                    diagnosis = selectedDiagnosis,
                    fatigueSensitivity = fatigueSensitivity.toIntOrNull(),
                    activityBaseline = activityBaseline.toIntOrNull(),
                    anaerobicThreshold = anaerobicThreshold.toIntOrNull(),
                    bellScale = bellScale.toIntOrNull(),
                    fitnessTracker = fitnessTracker.takeIf { it.isNotBlank() }
                )
                Log.d("UserProfileScreen", "Saving profile: $updatedProfile")
                viewModel.saveProfile(updatedProfile)
                //onSaveProfile(updatedProfile)
                Log.d("UserProfileScreen", "Profile saved, navigating back to settings")
                navController.navigate(Route.SETTINGS) {
                    popUpTo(Route.USERPROFILE) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_save_profile))
        }
    }
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(stringResource(R.string.dialog_unsaved_title)) },
            text = { Text(stringResource(R.string.dialog_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        navController.popBackStack() // Seite verlassen
                    }
                ) {
                    Text(stringResource(R.string.dialog_unsaved_confirm_leave))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                    } // Dialog schließen, auf Seite bleiben
                ) {
                    Text(stringResource(R.string.dialog_unsaved_dismiss_cancel))
                }
            }
        )
    }
}

@Composable
fun DropdownMenuField(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

class UserProfileViewModel(
    private val dao: UserProfileDao
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfileEntry?>(null)
    val profile: StateFlow<UserProfileEntry?> = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getProfileLive().collect { userProfile ->
                _profile.value = userProfile ?: createPlaceholder()
            }
        }
    }

    fun saveProfile(profile: UserProfileEntry) {
        viewModelScope.launch {
            dao.insertOrUpdate(profile)
        }
    }

    private fun createPlaceholder(): UserProfileEntry {
        return UserProfileEntry(
            userId = "",
            nickname = null,
            sex = UserProfileEntry.Sex.UNSPECIFIED,
            birthYear = null,
            heightCm = null,
            weightKg = null,
            restingHeartRateBpm = null,
            amputationLevel = UserProfileEntry.AmputationLevel.NONE,
            fatigueSensitivity = null,
            activityBaseline = null,
            anaerobicThreshold = null,
            bellScale = null,
            illnessStartDate = null,
            diagnosis = null,
            fitnessTracker = null
        )
    }
}
