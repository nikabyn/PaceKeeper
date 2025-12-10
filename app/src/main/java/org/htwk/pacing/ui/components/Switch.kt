package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp


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
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
            // .align(Alignment.CenterVertically)
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
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
