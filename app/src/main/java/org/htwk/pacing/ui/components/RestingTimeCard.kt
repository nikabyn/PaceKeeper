package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.CardStyle

@Composable
fun RestingHoursCard(
    restingStart: String,
    restingEnd: String,
    onEditClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Text(
                text = stringResource(R.string.personal_resting_hours),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "$restingStart - $restingEnd",
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(onClick = onEditClick) {
                    Text("Edit")
                }
            }
        }
    }
}
