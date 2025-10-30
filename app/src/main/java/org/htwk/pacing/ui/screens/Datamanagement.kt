package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(navController: NavController) {
    var items by remember {
        mutableStateOf(
            listOf(
                "Data 1" to false,
                "Data 2" to false,
                "Data 3" to false
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datenmanagement") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            items.forEachIndexed { index, (title, checked) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            items = items.toMutableList().apply {
                                set(index, title to newChecked)
                            }
                        }
                    )
                    Text(title, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
