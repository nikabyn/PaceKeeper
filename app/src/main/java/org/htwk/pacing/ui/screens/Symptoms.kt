package org.htwk.pacing.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.FeelingEntry
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.database.Symptom
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.DemoBanner
import org.htwk.pacing.ui.components.ModeViewModel
import org.htwk.pacing.ui.components.SymptomSelectionCard
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

/**
 * Overlay screen that allows the user to select and create new symptoms
 * to be associated with how they feel at a point in time.
 */
@Composable
fun SymptomScreen(
    navController: NavController,
    feeling: Feeling,
    viewModel: SymptomsViewModel = koinViewModel(),
    modeViewModel: ModeViewModel = koinViewModel()
) {
    var openDialog by remember { mutableStateOf(false) }
    val symptoms by viewModel.symptoms.collectAsState()

    LaunchedEffect(symptoms) {
        viewModel.defaultSymptoms(symptoms)
    }

    val symptomWithStrength = remember { mutableStateMapOf<String, Int>() }

    Scaffold(
        modifier = Modifier.testTag("SymptomsScreen"),
        topBar = {
            TopBar(navController, onApply = {
                viewModel.storeEntry(
                    feeling,
                    symptomWithStrength.map {
                        Symptom(it.key, it.value)
                    }.toList()
                )
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openDialog = true },
                modifier = Modifier.testTag("SymptomsScreenAddButton")
            ) {
                Icon(Icons.Filled.Add, "Floating action button.")
            }
        }) { contentPadding ->
        Column {
            DemoBanner(modeViewModel = modeViewModel)

        Column(
            modifier = Modifier
                .padding(
                    horizontal = Spacing.large,
                    vertical = contentPadding.calculateTopPadding()
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("SymptomsScreenCheckboxes")
        ) {
            symptoms?.forEach { s ->
                SymptomSelectionCard(
                    name = s.name,
                    strength = symptomWithStrength[s.name] ?: s.strength,
                    onStrengthChange = { newStrength ->
                        symptomWithStrength[s.name] = newStrength
                    }
                )
            }
        }

        if (openDialog) {
            AddSymptomDialog(
                onCancel = {
                    openDialog = false
                },
                onConfirm = { newSymptom ->
                    openDialog = false
                    viewModel.storeSymptom(newSymptom)
                },
                symptoms,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(navController: NavController, onApply: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("SymptomsScreenBackButton")
            ) {
                Icon(
                    painterResource(R.drawable.rounded_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.select_symptoms),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            org.htwk.pacing.ui.components.Button(
                onClick = {
                    onApply()
                    navController.navigate(Route.HOME)
                },
                style = org.htwk.pacing.ui.theme.PrimaryButtonStyle,
                modifier = Modifier.testTag("SymptomsScreenApplyButton")
            ) {
                Image(
                    painter = painterResource(R.drawable.settings_save_icon),
                    contentDescription = stringResource(R.string.save),
                    colorFilter = ColorFilter.tint(
                        MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save))

            }
            Spacer(Modifier.width(8.dp))
        }
    )
}


/**
 * Dialog for creating a new symptom.
 * Only accepts non empty strings.
 */
@Composable
fun AddSymptomDialog(
    onCancel: () -> Unit,
    onConfirm: (newSymptom: String) -> Unit,
    symptoms: List<Symptom>?,
) {
    var newSymptom by remember { mutableStateOf("") }
    var isEmpty by remember { mutableStateOf(true) }
    var symptomAlreadyExists by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.testTag("AddSymptomDialog"),
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.add_symptom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    modifier = Modifier.testTag("AddSymptomTextField"),
                    value = newSymptom,
                    onValueChange = {
                        if (it.endsWith("\n")) return@TextField
                        symptomAlreadyExists = symptoms.orEmpty().any { s ->
                            s.name.equals(newSymptom.trim(), ignoreCase = true)
                        }
                        newSymptom = it
                        isEmpty = newSymptom.trim().isEmpty()
                    }
                )
                if (isEmpty) {
                    Text(
                        stringResource(R.string.should_not_be_empty),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (symptomAlreadyExists) {
                    Text(
                        stringResource(R.string.symptom_already_exists),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEmpty || symptomAlreadyExists) return@Button
                    onConfirm(newSymptom.trim())
                },
                modifier = Modifier.testTag("AddSymptomConfirmButton")
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("AddSymptomDismissButton")
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    )
}

class SymptomsViewModel(
    private val manualSymptomDao: ManualSymptomDao,
) : ViewModel() {
    val symptoms = manualSymptomDao
        .getAllSymptomsLive()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun storeSymptom(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            manualSymptomDao.insertSymptom(Symptom(name = name, strength = 0))
        }
    }

    fun storeEntry(feeling: Feeling, symptoms: List<Symptom>) {
        CoroutineScope(Dispatchers.IO).launch {
            val now = Clock.System.now()
            val entry = ManualSymptomEntry(feeling = FeelingEntry(now, feeling), symptoms)
            manualSymptomDao.insert(entry)
        }
    }

    fun defaultSymptoms(symptoms: List<Symptom>?) {
        if (!symptoms.isNullOrEmpty()) return

        val defaults = listOf("fatigue", "headache", "brain_fog")
        defaults.forEach { storeSymptom(it) }
    }
}
