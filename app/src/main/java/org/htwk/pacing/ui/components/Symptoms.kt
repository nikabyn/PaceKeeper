package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.Symptom
import org.htwk.pacing.ui.Route
import org.koin.androidx.compose.koinViewModel

@Composable
fun SymptomSelectionCard(navController: NavController) {
    val red = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFEF5350)
    val orange = if (isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFFEC9C29)
    val yellow = if (isSystemInDarkTheme()) Color(0xFFE6EE9C) else Color(0xFFA8B90C)
    val green = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF66BB6A)

    CardWithTitle("Track Symptoms") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { navController.navigate(Route.Symptoms) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.very_sad),
                    contentDescription = "very sad",
                    tint = red,
                    modifier = Modifier.background(red.copy(alpha = 0.3f)),
                )
            }
            IconButton(onClick = { navController.navigate(Route.Symptoms) }) {
                Icon(
                    painter = painterResource(R.drawable.sad),
                    "sad",
                    tint = orange,
                    modifier = Modifier.background(orange.copy(alpha = 0.3f)),
                )
            }
            IconButton(onClick = { navController.navigate(Route.Symptoms) }) {
                Icon(
                    painter = painterResource(R.drawable.happy),
                    "happy",
                    tint = yellow,
                    modifier = Modifier.background(yellow.copy(alpha = 0.3f)),
                )
            }
            IconButton(onClick = { navController.navigate(Route.Symptoms) }) {
                Icon(
                    painter = painterResource(R.drawable.very_happy),
                    "very happy",
                    tint = green,
                    modifier = Modifier.background(green.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(
    navController: NavController,
    viewModel: SymptomsViewModel = koinViewModel(),
) {
    var openDialog by remember { mutableStateOf(false) }
    val symptoms by viewModel.symptoms.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                title = {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Select Symptoms", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Why are you feeling really bad?",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Route.Home) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { navController.navigate(Route.Home) },
                        contentPadding = PaddingValues(all = 0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Apply")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            )
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
            symptoms.forEach { SymptomCheckBox(it.name) }
        }

        if (openDialog) {
            AddSymptomDialog(
                onCancel = {
                    openDialog = false
                },
                onConfirm = { newSymptom ->
                    openDialog = false
                    viewModel.addSymptom(newSymptom)
                })
        }
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

    fun addSymptom(name: String) {
        viewModelScope.launch {
            manualSymptomDao.insertSymptom(Symptom(name))
        }
    }
}

@Composable
fun SymptomCheckBox(title: String) {
    var checked by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = { checked = !checked })
            .fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { checked = it }
        )
        Text(title)
    }
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