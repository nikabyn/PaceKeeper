package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
// Import der Datenklassen, die in der Datenbank-Datei definiert sind
import org.htwk.pacing.backend.database.* import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Screen zur Eingabe und Bearbeitung des Benutzerprofils.
 * * @param onNavigateBack Lambda zum Verlassen des Screens.
 * @param onSaveProfile Lambda, das aufgerufen wird, um das Profil zu speichern.
 * @param initialProfile Das aktuell geladene Profil (oder ein Platzhalter-Profil).
 */
@Composable
fun UserProfileScreen(
    navController: NavController,
    onSaveProfile: (UserProfile) -> Unit,
    initialProfile: UserProfile = createInitialPlaceholderProfile()
) {
    // 1. Lokale Zustände (State) für die Eingabefelder
    var nickname by remember { mutableStateOf(initialProfile.nickname ?: "") }
    var birthYear by remember { mutableStateOf(initialProfile.birthYear?.toString() ?: "") }
    var heightCm by remember { mutableStateOf(initialProfile.heightCm?.toString() ?: "") }
    var weightKg by remember { mutableStateOf(initialProfile.weightKg?.toString() ?: "") }
    var fatigueSensitivity by remember { mutableStateOf(initialProfile.fatigueSensitivity?.toString() ?: "") }
    var bellScale by remember { mutableStateOf(initialProfile.bellScale?.toString() ?: "") }
    var fitnessTracker by remember { mutableStateOf(initialProfile.fitnessTracker ?: "") }
    var sex by remember { mutableStateOf(initialProfile.sex) }
    var amputationLevel by remember { mutableStateOf(initialProfile.amputationLevel) }
    var diagnosis by remember { mutableStateOf(initialProfile.diagnosis) }
    var illnessStartDate by remember { mutableStateOf(initialProfile.illnessStartDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // --- Navigation / Header Ersatz ---
        /*Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Start) {
            Button(onClick = onNavigateBack) {
                Text("Zurück") // Hardcoded String
            }
        }*/

        // --- 1. Allgemeine Daten ---
        Spacer(Modifier.height(16.dp))
        SectionTitle("Allgemeine Daten") // Hardcoded String

        CustomOutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = "Spitzname"
        )
        SexDropdown(selectedSex = sex, onSexSelected = { sex = it })
        CustomOutlinedTextField(
            value = birthYear,
            onValueChange = { birthYear = it.filter { char -> char.isDigit() }.take(4) },
            label = "Geburtsjahr (z.B. 1990)",
            keyboardType = KeyboardType.Number
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomOutlinedTextField(
                value = heightCm,
                onValueChange = { heightCm = it.filter { char -> char.isDigit() } },
                label = "Größe (cm)",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            CustomOutlinedTextField(
                value = weightKg,
                onValueChange = { weightKg = it.filter { char -> char.isDigit() } },
                label = "Gewicht (kg)",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }

        // --- 2. Pacing-Relevante Daten ---
        SectionTitle("Pacing-Einstellungen") // Hardcoded String

        AmputationLevelDropdown(
            selectedLevel = amputationLevel,
            onLevelSelected = { amputationLevel = it }
        )

        CustomOutlinedTextField(
            value = fatigueSensitivity,
            onValueChange = { fatigueSensitivity = it.filter { char -> char.isDigit() }.take(2) },
            label = "Ermüdungsempfindlichkeit (1-10)",
            keyboardType = KeyboardType.Number
        )

        CustomOutlinedTextField(
            value = bellScale,
            onValueChange = { bellScale = it.filter { char -> char.isDigit() }.take(2) },
            label = "BELL-Skala (Belastungsempfinden 1-10)",
            keyboardType = KeyboardType.Number
        )

        // --- 3. Krankheits- und Tracker-Daten ---
        SectionTitle("Krankheit & Tracker") // Hardcoded String

        DiagnosisDropdown(selectedDiagnosis = diagnosis, onDiagnosisSelected = { diagnosis = it })

        IllnessStartDatePicker(
            selectedTimestamp = illnessStartDate,
            onDateSelected = { illnessStartDate = it }
        )

        CustomOutlinedTextField(
            value = fitnessTracker,
            onValueChange = { fitnessTracker = it },
            label = "Fitness-Tracker (z.B. Garmin)"
        )

        Spacer(Modifier.height(32.dp))

        // Speichern Button
        Button(
            onClick = {
                val profileToSave = createProfileFromState(
                    initialProfile = initialProfile,
                    nickname = nickname,
                    sex = sex,
                    birthYear = birthYear,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    amputationLevel = amputationLevel,
                    fatigueSensitivity = fatigueSensitivity,
                    bellScale = bellScale,
                    illnessStartDate = illnessStartDate,
                    diagnosis = diagnosis,
                    fitnessTracker = fitnessTracker
                )
                onSaveProfile(profileToSave)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Profil speichern") // Hardcoded String
        }
    }
}

// --- Hilfsfunktionen und Composables ---

private fun createProfileFromState(
    initialProfile: UserProfile,
    nickname: String,
    sex: Sex,
    birthYear: String,
    heightCm: String,
    weightKg: String,
    amputationLevel: AmputationLevel?,
    fatigueSensitivity: String,
    bellScale: String,
    illnessStartDate: Long?,
    diagnosis: Diagnosis?,
    fitnessTracker: String
): UserProfile {
    return initialProfile.copy(
        nickname = nickname.takeIf { it.isNotBlank() },
        sex = sex,
        birthYear = birthYear.toIntOrNull(),
        heightCm = heightCm.toIntOrNull(),
        weightKg = weightKg.toIntOrNull(),
        amputationLevel = amputationLevel,
        fatigueSensitivity = fatigueSensitivity.toIntOrNull(),
        bellScale = bellScale.toIntOrNull(),
        illnessStartDate = illnessStartDate,
        diagnosis = diagnosis,
        fitnessTracker = fitnessTracker.takeIf { it.isNotBlank() },
        userId = initialProfile.userId.ifBlank { UUID.randomUUID().toString() }
    )
}

fun createInitialPlaceholderProfile(): UserProfile {
    return UserProfile(
        id = 0,
        userId = "",
        nickname = null,
        sex = Sex.UNSPECIFIED,
        birthYear = null,
        heightCm = null,
        weightKg = null,
        amputationLevel = AmputationLevel.NONE,
        fatigueSensitivity = null,
        activityBaseline = null,
        anaerobicThreshold = null,
        bellScale = null,
        illnessStartDate = null,
        diagnosis = null,
        fitnessTracker = null
    )
}


@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String, // Hardcoded String für das Label
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SexDropdown(selectedSex: Sex, onSexSelected: (Sex) -> Unit) {
    Text("Geschlecht: ${selectedSex.name}", style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun AmputationLevelDropdown(selectedLevel: AmputationLevel?, onLevelSelected: (AmputationLevel?) -> Unit) {
    Text("Amputation: ${selectedLevel?.name ?: "Keine Angabe"}", style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun DiagnosisDropdown(selectedDiagnosis: Diagnosis?, onDiagnosisSelected: (Diagnosis?) -> Unit) {
    Text("Diagnose: ${selectedDiagnosis?.name ?: "Keine Angabe"}", style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun IllnessStartDatePicker(selectedTimestamp: Long?, onDateSelected: (Long?) -> Unit) {
    val dateText = selectedTimestamp?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalDate().toString()
    } ?: "Datum auswählen" // Hardcoded String

    Button(onClick = {
        println("Date Picker geöffnet")
    }) {
        Text("Erkrankungsbeginn: $dateText") // Hardcoded String
    }
}