package org.htwk.pacing.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import org.htwk.pacing.ui.Route
import androidx.navigation.NavController




/**
 * Eine klickbare Card im Stil der HeartRateCard zur Navigation zum Benutzerprofil.
 *
 * @param onClick Lambda, das ausgeführt wird, wenn die Karte angeklickt wird (Navigation).
 * @param modifier Der Modifier für diese Komponente.
 */
@Composable
fun UserProfileCard(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { navController.navigate(Route.USERPROFILE) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profil",
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Benutzerprofil", style = MaterialTheme.typography.titleMedium)
                Text("Profil bearbeiten", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
