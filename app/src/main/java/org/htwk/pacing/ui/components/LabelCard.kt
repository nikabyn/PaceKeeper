package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.htwk.pacing.ui.theme.extendedColors
import kotlin.math.floor

@Composable
fun LabelCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    modifier: Modifier = Modifier,
) {
    val energyLevel = floor(energy * 6).toInt().coerceIn(0, 5)

    val (labelText, level) = when (energyLevel) {
        0 -> Pair(stringResource(R.string.label_energy_level_very_low), Level.Error)
        1 -> Pair(stringResource(R.string.label_energy_level_low), Level.Error)
        2, 3 -> Pair(stringResource(R.string.label_energy_level_moderate), Level.Warning)
        4 -> Pair(stringResource(R.string.label_energy_level_high), Level.Info)
        else -> Pair(stringResource(R.string.label_energy_level_very_high), Level.Info)
    }

    val (icon, cardColors) = when (level) {
        Level.Error -> Pair(
            painterResource(R.drawable.ic_energy_level_very_low),
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        )

        Level.Warning -> Pair(
            painterResource(R.drawable.ic_energy_level_moderate),
            CardDefaults.cardColors(
                containerColor = MaterialTheme.extendedColors.yellow.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
                contentColor = MaterialTheme.extendedColors.yellow.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.onSurface),
            ),
        )

        Level.Info -> Pair(
            painterResource(R.drawable.ic_energy_level_very_high),
            CardDefaults.cardColors(
                containerColor = MaterialTheme.extendedColors.blue.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
                contentColor = MaterialTheme.extendedColors.blue.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.onSurface),
            ),
        )
    }

    Card(
        colors = cardColors,
        shape = CardStyle.shape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
        ) {

            Icon(
                painter = icon,
                contentDescription = stringResource(R.string.label_energy_icon_name),
                modifier = Modifier.size(32.dp)
            )

            Column {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "bottom text",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private enum class Level {
    Error,
    Warning,
    Info,
}