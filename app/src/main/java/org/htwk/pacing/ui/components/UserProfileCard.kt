package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing

/**
 * A UI component for displaying
 * the User Profile. This card serves as a navigation element,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.largeIncreased,
                )
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings_person),
                    contentDescription = stringResource(R.string.icon_profile_description),
                    modifier = Modifier
                        .padding(top = Spacing.small)
                        .padding(bottom = Spacing.large)
                        .size(27.dp)
                )
                Text(
                    text = stringResource(R.string.title_settings_unknown_user),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.subtitle_settings_user_profile),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
