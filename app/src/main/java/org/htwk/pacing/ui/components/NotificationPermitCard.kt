package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.CardStyle


/**
 * A Composable that displays notification permission switches in a card.
 *
 * @param warningPermit Whether the user has permitted warnings.
 * @param onWarningChange A lambda to be invoked when the warning switch is changed.
 */

@Composable
fun NotificationPermitCard(
    warningPermit: Boolean,
    onWarningChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                title = stringResource(R.string.warnings),
                description = stringResource(R.string.warning_description),
                checked = warningPermit,
                onCheckedChange = onWarningChange,
                titleStyle = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun Switch(
    title: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = title,
                style = titleStyle
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = descriptionStyle,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            // Explicitly call the Material Design Switch to prevent recursion
            Switch(
                checked = checked,
                onCheckedChange = null // The toggleable modifier handles the change
            )
        }
    }
}



