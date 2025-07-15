package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import org.htwk.pacing.R
import kotlin.math.ceil

@Composable
fun LabelCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    modifier: Modifier = Modifier,
) {
    val energyLevel = if (energy == 0.0) 1 else ceil(energy * 6).toInt().coerceIn(1, 6)

    val (labelText, icon: Painter) = when (energyLevel) {
        1 -> Pair(stringResource(R.string.label_energy_level_very_low), painterResource(R.drawable.ic_energy_level_very_low))
        2 -> Pair(stringResource(R.string.label_energy_level_low), painterResource(R.drawable.ic_energy_level_low))
        3,4 -> Pair(stringResource(R.string.label_energy_level_moderate), painterResource(R.drawable.ic_energy_level_moderate))
        5 -> Pair(stringResource(R.string.label_energy_level_high), painterResource(R.drawable.ic_energy_level_high))
        else -> Pair(stringResource(R.string.label_energy_level_very_high), painterResource(R.drawable.ic_energy_level_very_high))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {

        Icon(
            painter = icon,
            contentDescription = "Energy level icon",
            tint = Color.Unspecified,
            modifier = Modifier.size(50.dp)
        )

        Text(
            text = labelText,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
