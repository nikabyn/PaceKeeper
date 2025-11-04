package org.htwk.pacing.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.htwk.pacing.backend.data_collection.health_connect.wantedPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val client = remember { HealthConnectClient.getOrCreate(context) }
    val coroutineScope = rememberCoroutineScope()

    var grantedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        grantedPermissions = client.permissionController.getGrantedPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("Permissions", "Granted permissions: $granted")
        coroutineScope.launch {
            grantedPermissions = client.permissionController.getGrantedPermissions()
        }
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
            wantedPermissions.forEach { permission ->
                val checked = grantedPermissions.contains(permission)
                val label = permission.substringAfterLast('.')

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            if (newChecked) {
                                permissionLauncher.launch(setOf(permission))
                            } else {
                                val intent =
                                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )
                    Text(label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
