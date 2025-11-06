package org.htwk.pacing.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.htwk.pacing.backend.database.UserProfile
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.ui.Route
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UserProfileScreen(
    navController: NavController,
    viewModel: UserProfileViewModel,
    onSaveProfile: (UserProfile) -> Unit
) {
    val profileState by viewModel.profile.collectAsState()
    val profile = profileState ?: return

    // lokale States für Textfelder
    var nickname by remember { mutableStateOf(profile.nickname ?: "") }
    var birthYear by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var heightCm by remember { mutableStateOf(profile.heightCm?.toString() ?: "") }
    var weightKg by remember { mutableStateOf(profile.weightKg?.toString() ?: "") }
    var selectedSex by remember { mutableStateOf(profile.sex) }
    var selectedAmputationLevel by remember { mutableStateOf(profile.amputationLevel) }
    var selectedDiagnosis by remember { mutableStateOf(profile.diagnosis) }
    var fatigueSensitivity by remember { mutableStateOf(profile.fatigueSensitivity?.toString() ?: "") }
    var activityBaseline by remember { mutableStateOf(profile.activityBaseline?.toString() ?: "") }
    var anaerobicThreshold by remember { mutableStateOf(profile.anaerobicThreshold?.toString() ?: "") }
    var bellScale by remember { mutableStateOf(profile.bellScale?.toString() ?: "") }
    var fitnessTracker by remember { mutableStateOf(profile.fitnessTracker ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zurück Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
            }
            Text("Benutzerprofil", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Spitzname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = birthYear,
            onValueChange = { birthYear = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Geburtsjahr") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = heightCm,
            onValueChange = { heightCm = it.filter { c -> c.isDigit() } },
            label = { Text("Größe (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = weightKg,
            onValueChange = { weightKg = it.filter { c -> c.isDigit() } },
            label = { Text("Gewicht (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Geschlecht
        DropdownMenuField(
            label = "Geschlecht",
            options = UserProfile.Sex.entries.map { it.name },
            selectedOption = selectedSex.name,
            onOptionSelected = { selectedSex = UserProfile.Sex.valueOf(it) }
        )

        // Amputationslevel
        DropdownMenuField(
            label = "Amputationslevel",
            options = UserProfile.AmputationLevel.entries.map { it.name },
            selectedOption = selectedAmputationLevel?.name ?: "NONE",
            onOptionSelected = { selectedAmputationLevel = UserProfile.AmputationLevel.valueOf(it) }
        )

        // Diagnose
        DropdownMenuField(
            label = "Diagnose",
            options = listOf("Keine") + UserProfile.Diagnosis.entries.map { it.name },
            selectedOption = selectedDiagnosis?.name ?: "Keine",
            onOptionSelected = { 
                selectedDiagnosis = if (it == "Keine") null else UserProfile.Diagnosis.valueOf(it)
            }
        )

        OutlinedTextField(
            value = fatigueSensitivity,
            onValueChange = { fatigueSensitivity = it.filter { c -> c.isDigit() } },
            label = { Text("Müdigkeitsempfindlichkeit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = activityBaseline,
            onValueChange = { activityBaseline = it.filter { c -> c.isDigit() } },
            label = { Text("Aktivitäts-Baseline") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = anaerobicThreshold,
            onValueChange = { anaerobicThreshold = it.filter { c -> c.isDigit() } },
            label = { Text("Anaerobische Schwelle") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = bellScale,
            onValueChange = { bellScale = it.filter { c -> c.isDigit() } },
            label = { Text("Bell-Skala") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = fitnessTracker,
            onValueChange = { fitnessTracker = it },
            label = { Text("Fitness-Tracker") },
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
                Log.d("UserProfileScreen", "Profile saved, navigating back to settings")
                navController.navigate(Route.SETTINGS) {
                    popUpTo(Route.USERPROFILE) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Profil speichern")
        }
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

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getCurrentProfile().collect { userProfile ->
                _profile.value = userProfile ?: createPlaceholder()
            }
        }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            dao.insertOrUpdate(profile)
        }
    }

    /**
     * Speichert oder aktualisiert das Profil, ähnlich wie storeRecords().
     */
    fun storeProfile(profile: UserProfile) {
        saveProfile(profile)
    }

    private fun createPlaceholder(): UserProfile {
        return UserProfile(
            userId = "",
            nickname = null,
            sex = UserProfile.Sex.UNSPECIFIED,
            birthYear = null,
            heightCm = null,
            weightKg = null,
            amputationLevel = UserProfile.AmputationLevel.NONE,
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
