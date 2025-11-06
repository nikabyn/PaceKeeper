package org.htwk.pacing.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R // Import notwendig, um auf R.string.* zuzugreifen
import androidx.navigation.NavHostController
import org.htwk.pacing.ui.Route


/**
 * Eine klickbare Card im Stil der HeartRateCard zur Navigation zum Benutzerprofil.
 *
 * @param onClick Lambda, das ausgeführt wird, wenn die Karte angeklickt wird (Navigation).
 * @param modifier Der Modifier für diese Komponente.
 */
@Composable
fun UserProfileCard(
    // Parameter ist optional (Standardwert ist eine leere Aktion),
    // damit die Card ohne Argumente eingebunden werden kann.
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Fügt Klickbarkeit und Ripple-Effekt hinzu
            .clickable(onClick = onClick)
            // Stellt sicher, dass der Ripple-Effekt die abgerundeten Ecken respektiert
            .clip(RoundedCornerShape(12.dp))
            // Visuelle Darstellung wie bei HeartRateCard
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(10.dp) // Innere Polsterung
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp), // Zusätzliche Polsterung innen
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Linke Seite: Icon und Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    // Text aus String-Ressource
                    contentDescription = stringResource(R.string.icon_profile_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    // Text aus String-Ressource
                    Text(
                        text = stringResource(R.string.profile_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Text aus String-Ressource
                    Text(
                        text = stringResource(R.string.profile_card_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Rechte Seite: Einstellungs-Icon
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.icon_settings_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Button(
        onClick ={navController.navigate(Route.USERPROFILE)},
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("User Profile")
    }
}