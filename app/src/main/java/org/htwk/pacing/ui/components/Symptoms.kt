package org.htwk.pacing.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Column {
                        Text("Symptoms", style = MaterialTheme.typography.titleLarge)
                        Text("Select your symptoms", style = MaterialTheme.typography.titleSmall)
                    }

                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Route.Home) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }

            )
        }) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            CheckboxMinimalExample("Headache")
            CheckboxMinimalExample("Back pain")
            CheckboxMinimalExample("Nausea")
            CheckboxMinimalExample("Exhaustion")
        }
    }
}

@Composable
fun CheckboxMinimalExample(title: String) {
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