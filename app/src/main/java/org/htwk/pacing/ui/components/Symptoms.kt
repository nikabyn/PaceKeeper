package org.htwk.pacing.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.Route

@Composable
fun SymptomSelectionCard(navController: NavController) {
    CardWithTitle("Track Symptoms") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.navigate(Route.Symptoms) }) {
                Icon(
                    painter = painterResource(R.drawable.baseline_thumb_down_24),
                    "thumbs down"
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.baseline_thumb_down_24),
                    "thumbs down"
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.baseline_thumb_down_24),
                    "thumbs down"
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.baseline_thumb_down_24),
                    "thumbs down"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomScreen(navController: NavController) {
    var openDialog by remember { mutableStateOf(false) }
    var newSymptom by remember { mutableStateOf("") }

    var symptoms = remember { mutableListOf("Headache", "Exhaustion", "Nausea", "Back pain") }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Symptoms", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Select your symptoms",
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
                    Button(onClick = { navController.navigate(Route.Home) }) {
                        Text("Apply")
                    }
                }

            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openDialog = true },
            ) {
                Icon(Icons.Filled.Add, "Floating action button.")
            }
        }) { contentPadding ->

        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            symptoms.forEach { SymptomCheckBox(it) }
        }
        if (openDialog) {
            AlertDialog(
                onDismissRequest = {
                    openDialog = false
                },
                title = {
                    Text(text = "Add symptom")
                },
                text = {
                    TextField(
                        value = newSymptom,
                        onValueChange = { newSymptom = it }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            openDialog = false
                            symptoms.add(newSymptom)
                            newSymptom = ""
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { openDialog = false }
                    ) {
                        Text("Dismiss")
                    }
                }
            )
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
        Text(
            title
        )
    }
}