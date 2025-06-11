package org.htwk.pacing.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateRecord
import org.htwk.pacing.backend.data_collection.HealthConnectHelper

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

fun isHealthConnectInstalled(context: android.content.Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

@Composable
fun HeartRateScreen() {
    var permissionDenied = false
    val context = LocalContext.current
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            Log.d("HeartRateScreen", "Health Connect Permission granted!")
            HealthConnectHelper.readHeartRateData(context)
        } else {
            Log.d("HeartRateScreen", "Health Connect Permission NOT granted!")
            permissionDenied = true
            Toast.makeText(
                context,
                "geht nich",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Button(onClick = {
        if (isHealthConnectInstalled(context)) {
            permissionLauncher.launch(permissions)
        } else {
            /*Toast.makeText(
                context,
                "Health Connect ist nicht installiert! Bitte installiere die App aus dem Play Store.",
                Toast.LENGTH_LONG
            ).show()*/
            Log.d("HeartRateScreen", "Health Connect is NOT installed!")
        }
    }) {
        Text("Health Connect Berechtigung anfragen und Herzfrequenz auslesen")

    }
}
