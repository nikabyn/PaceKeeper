package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

            CategorySwitch(
                title = "Kategorie A",
                checked = catA,
                onCheckedChange = { viewModel.setCategoryA(it) }
            )

            CategorySwitch(
                title = "Kategorie B",
                checked = catB,
                onCheckedChange = { viewModel.setCategoryB(it) }
            )

            CategorySwitch(
                title = "Kategorie C",
                checked = catC,
                onCheckedChange = { viewModel.setCategoryC(it) }
            )
        }
    }
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

@Composable
fun CategorySwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

