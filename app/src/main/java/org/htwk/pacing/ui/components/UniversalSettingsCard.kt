package org.htwk.pacing.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.htwk.pacing.R
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing

/**
 * A universal reusable UI component (Composable) that displays a clickable card
 * for navigation in settings screens. This card can be customized with different
 * routes, names, descriptions, and icons.
 *
 * @param route The navigation route to navigate to when the card is clicked
 * @param name The title/name displayed on the card
 * @param description The description text displayed below the title
 * @param icon The Material Icon (ImageVector) displayed on the left side of the card (optional)
 * @param iconRes The Drawable resource ID for custom icons (optional)
 * @param navController The NavController used for navigation
 */
@Composable
fun UniversalSettingsCard(
    route: String,
    name: String,
    description: String,
    navController: NavController,
    icon: ImageVector? = null,
    style: CornerBasedShape,
    @DrawableRes iconRes: Int? = null
) {

    Card(
        onClick = { navController.navigate(route) },
        colors =
            if (route != Route.FEEDBACK)
                CardStyle.colors
            else
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        shape = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.largeIncreased,
                )
                .height(50.dp) //ensures same height for all cards even if subtitle is missing
        ) {
            // show icon - Material Icon or Drawable Resource
            when {
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        modifier = Modifier.size(22.dp)
                    )
                }

                iconRes != null -> {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = name,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (description.isNotEmpty()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                painterResource(R.drawable.rounded_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
