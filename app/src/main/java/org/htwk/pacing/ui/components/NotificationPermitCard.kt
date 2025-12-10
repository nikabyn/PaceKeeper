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

/**
 * A Composable that displays notification permission switches in a card.
 *
 * @param warningPermit Whether the user has permitted warnings.
 * @param reminderPermit Whether the user has permitted reminders.
 * @param suggestionPermit Whether the user has permitted suggestions.
 * @param onWarningChange A lambda to be invoked when the warning switch is changed.
 * @param onReminderChange A lambda to be invoked when the reminder switch is changed.
 * @param onSuggestionChange A lambda to be invoked when the suggestion switch is changed.
 */

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
