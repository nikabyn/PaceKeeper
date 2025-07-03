package org.htwk.pacing.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.FeelingEntry
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.database.Symptom
import org.htwk.pacing.ui.Route
import org.koin.androidx.compose.koinViewModel

@Composable
fun SymptomScreen(
    navController: NavController,
    feeling: Feeling,
    viewModel: SymptomsViewModel = koinViewModel(),
) {
    var openDialog by remember { mutableStateOf(false) }
    val symptoms by viewModel.symptoms.collectAsState()
    val selected = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopBar(navController, feeling, onApply = {
                viewModel.storeEntry(feeling, selected.map { Symptom(it) }.toList())
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openDialog = true }) {
                Icon(Icons.Filled.Add, "Floating action button.")
            }
        }) { contentPadding ->

        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            symptoms.forEach {
                SymptomCheckBox(
                    it.name,
                    onChange = { checked, name ->
                        if (checked && !selected.contains(name)) {
                            selected.add(name)
                            return@SymptomCheckBox
                        }
                        selected.remove(name)
                    })
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
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(navController: NavController, feeling: Feeling, onApply: () -> Unit) {
    val questionBasedOnFeeling = when (feeling) {
        Feeling.VeryBad -> "Feeling really bad?"
        Feeling.Bad -> "Feeling bad?"
        Feeling.Good -> "Feeling good?"
        Feeling.VeryGood -> "Feeling really good?"
    }

    TopAppBar(
        // TODO For much later: Maybe change the color based on the selected feeling?
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Select Symptoms", style = MaterialTheme.typography.titleLarge)
                    Text(
                        questionBasedOnFeeling,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            Button(
                onClick = {
                    onApply()
                    navController.navigate(Route.HOME)
                },
                contentPadding = PaddingValues(all = 0.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Apply")
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
    )
}

@Composable
fun AddSymptomDialog(
    onCancel: () -> Unit,
    onConfirm: (newSymptom: String) -> Unit,
) {
    var newSymptom by remember { mutableStateOf("") }
    var isEmpty by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Add symptom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = newSymptom,
                    onValueChange = {
                        if (it.endsWith("\n")) return@TextField
                        newSymptom = it
                        isEmpty = newSymptom.trim().isEmpty()
                    }
                )
                if (isEmpty) {
                    Text("Should not be empty", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (isEmpty) return@Button
                onConfirm(newSymptom.trim())
                newSymptom = ""
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun SymptomCheckBox(name: String, onChange: (state: Boolean, name: String) -> Unit) {
    var checked by remember { mutableStateOf(false) }

    fun update(newChecked: Boolean) {
        checked = newChecked
        onChange(checked, name)
        Log.d("SymptomCheckBox", "$checked")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = { update(!checked) })
            .fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { update(it) }
        )
        Text(name)
    }
}

class SymptomsViewModel(
    private val manualSymptomDao: ManualSymptomDao
) : ViewModel() {
    val symptoms: StateFlow<List<Symptom>> = manualSymptomDao
        .getAllSymptoms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun storeSymptom(name: String) {
        viewModelScope.launch {
            manualSymptomDao.insertSymptom(Symptom(name))
        }
    }

    fun storeEntry(feeling: Feeling, symptoms: List<Symptom>) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val entry = ManualSymptomEntry(feeling = FeelingEntry(now, feeling), symptoms)
            manualSymptomDao.insertManualSymptomEntry(entry)
        }
    }
}