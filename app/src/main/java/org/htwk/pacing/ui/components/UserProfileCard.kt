package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing

/**
 * A reusable UI component (Composable) that displays a clickable card
 * representing the User Profile. This card serves as a navigation element,
 * directing the user to the UserProfileScreen when clicked.
 */
@Composable
fun UserProfileCard(navController: NavController) {
    Card(
        onClick = { navController.navigate(Route.USERPROFILE) },
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            modifier = Modifier.padding(
                horizontal = Spacing.large,
                vertical = Spacing.largeIncreased,
            )
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = stringResource(R.string.icon_profile_description),
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    stringResource(R.string.profile_card_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.profile_card_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
