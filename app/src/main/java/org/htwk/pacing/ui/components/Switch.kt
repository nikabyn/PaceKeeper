package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
@Composable
fun Switch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = textStyle
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

 */ @Composable
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Linke Spalte: Titel und Beschreibung
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
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

        // Rechte Spalte: Switch in einem Column für exakte Zentrierung
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}