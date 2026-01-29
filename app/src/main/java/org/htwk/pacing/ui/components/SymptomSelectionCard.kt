package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.Spacing
import org.htwk.pacing.ui.theme.extendedColors
import kotlin.math.roundToInt

@Composable
fun SymptomSelectionCard(
    name: String,
    strength: Int,
    onStrengthChange: (Int) -> Unit
) {
    Spacer(modifier = Modifier.height(Spacing.large))
    CardWithTitle(
        title = when (name) {
            "fatigue" -> stringResource(R.string.fatigue)
            "headache" -> stringResource(R.string.headache)
            "brain_fog" -> stringResource(R.string.brain_fog)
            else -> name
        },
        modifier = Modifier.testTag("SymptomSelectionCard_$name")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.width(16.dp))
            SymptomSlider(
                strength = strength,
                onStrengthChange = onStrengthChange,
                symptomName = name
            )
        }
    }
}

@Composable
fun SymptomSlider(
    strength: Int,
    onStrengthChange: (Int) -> Unit,
    symptomName: String = ""
) {
    val red = MaterialTheme.extendedColors.red
    val orange = MaterialTheme.extendedColors.orange
    val yellow = MaterialTheme.extendedColors.yellow
    val green = MaterialTheme.extendedColors.green

    var sliderPosition by remember(strength) {
        mutableFloatStateOf(strength.toFloat())
    }

    Slider(
        value = sliderPosition,
        onValueChange = {
            sliderPosition = it
            onStrengthChange(it.roundToInt())
        },
        colors = when (sliderPosition.toInt()) {
            0 -> SliderDefaults.colors(
                thumbColor = green,
                activeTrackColor = green
            )

            1 -> SliderDefaults.colors(
                thumbColor = yellow,
                activeTrackColor = yellow
            )

            2 -> SliderDefaults.colors(
                thumbColor = orange,
                activeTrackColor = orange
            )

            3 -> SliderDefaults.colors(
                thumbColor = red,
                activeTrackColor = red
            )

            else -> SliderDefaults.colors()
        },
        steps = 2,
        valueRange = 0f..3f,
        modifier = Modifier.testTag("SymptomSlider_$symptomName")
    )
}