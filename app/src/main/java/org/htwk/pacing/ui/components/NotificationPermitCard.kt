package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.htwk.pacing.ui.theme.CardStyle

@Composable

fun NotificationPermitCard(
    catA: Boolean,
    catB: Boolean,
    catC: Boolean,
    onAChange: (Boolean) -> Unit,
    onBChange: (Boolean) -> Unit,
    onCChange: (Boolean) -> Unit
) {
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {

            Switch(
                title = "Kategorie A",
                checked = catA,
                onCheckedChange = onAChange
            )

            Switch(
                title = "Kategorie B",
                checked = catB,
                onCheckedChange = onBChange
            )

            Switch(
                title = "Kategorie C",
                checked = catC,
                onCheckedChange = onCChange
            )
        }
    }
}
