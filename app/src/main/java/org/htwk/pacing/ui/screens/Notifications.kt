package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.htwk.pacing.ui.components.NotificationPermitCard
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel()
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Benachrichtigungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        var showDialog by remember { mutableStateOf(false) }
        var restingStart by remember { mutableStateOf("22:00") }
        var restingEnd by remember { mutableStateOf("06:00") }

        if (showDialog) {
            RestingHoursDialog(
                currentStart = restingStart,
                currentEnd = restingEnd,
                onDismiss = { showDialog = false },
                onConfirm = { newStart, newEnd ->
                    restingStart = newStart
                    restingEnd = newEnd
                    showDialog = false
                }
            )
        }


        val catA by viewModel.categoryA.collectAsState()
        val catB by viewModel.categoryB.collectAsState()
        val catC by viewModel.categoryC.collectAsState()

        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            NotificationPermitCard(
                catA = catA,
                catB = catB,
                catC = catC,
                onAChange = { viewModel.setCategoryA(it) },
                onBChange = { viewModel.setCategoryB(it) },
                onCChange = { viewModel.setCategoryC(it) }
            )
        }
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resting Hours bearbeiten") },
        text = {
            Column {

                androidx.compose.material3.OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start (HH:MM)") },
                    singleLine = true
                )

                androidx.compose.material3.OutlinedTextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("End (HH:MM)") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(start, end) }
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

class NotificationsViewModel(
    //context: Context
) : ViewModel() {

    private val _categoryA = MutableStateFlow(false)
    val categoryA: StateFlow<Boolean> = _categoryA

    private val _categoryB = MutableStateFlow(false)
    val categoryB: StateFlow<Boolean> = _categoryB

    private val _categoryC = MutableStateFlow(false)
    val categoryC: StateFlow<Boolean> = _categoryC

    fun setCategoryA(v: Boolean) {
        _categoryA.value = v
    }

    fun setCategoryB(v: Boolean) {
        _categoryB.value = v
    }

    fun setCategoryC(v: Boolean) {
        _categoryC.value = v
    }
}
