/*
package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Switch(
                title = "Kategorie A",
                description = "Eine Beschreibung",
                checked = catA,
                onCheckedChange = onAChange,
                titleStyle = MaterialTheme.typography.titleLarge
            )

            Switch(
                title = "Kategorie B",
                description = "Eine Beschreibung",
                checked = catB,
                onCheckedChange = onBChange,
                titleStyle = MaterialTheme.typography.titleLarge
            )

            Switch(
                title = "Kategorie C",
                description = "Eine Beschreibung",
                checked = catC,
                onCheckedChange = onCChange,
                titleStyle = MaterialTheme.typography.titleLarge
            )
        }
    }
}
*/
package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.CardStyle

@Composable
fun NotificationPermitCard(
    warningPermit: Boolean,
    reminderPermit: Boolean,
    suggestionPermit: Boolean,
    onWarningChange: (Boolean) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onSuggestionChange: (Boolean) -> Unit,
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
                titleStyle = MaterialTheme.typography.titleLarge
            )

            Switch(
                title = stringResource(R.string.reminders),
                description = stringResource(R.string.reminder_description),
                checked = reminderPermit,
                onCheckedChange = onReminderChange,
                titleStyle = MaterialTheme.typography.titleLarge
            )

            Switch(
                title = stringResource(R.string.suggestions),
                description = stringResource(R.string.suggestion_description),
                checked = suggestionPermit,
                onCheckedChange = onSuggestionChange,
                titleStyle = MaterialTheme.typography.titleLarge
            )

        }
    }
}
