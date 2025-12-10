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

/**
 * A Composable that displays the user's resting hours in a card.
 *
 * @param restingStart The start time of the resting period.
 * @param restingEnd The end time of the resting period.
 * @param onEditClick A lambda to be invoked when the edit button is clicked.
 */
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.personal_resting_hours),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "$restingStart - $restingEnd",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onEditClick) {
                Text(stringResource(id = R.string.edit))
            }
        }
    }
}
