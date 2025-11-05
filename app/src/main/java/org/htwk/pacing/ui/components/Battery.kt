package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.screens.HomeViewModel

/**
 * Shows the current energy level using 1 to 6 colored bars (a battery).
 */
@Composable
fun BatteryCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val adjustingEnergy = remember { mutableStateOf(false) }
    val adjustedEnergy = remember { mutableDoubleStateOf(energy) }

    val gradientColors = arrayListOf(
        Color(0xFFEC4242), // Red
        Color(0xFFE1C508), // Yellow
        Color(0xFF72D207), // Green
    )

    CardWithTitle(
        title = stringResource(R.string.current_energy),
        modifier = modifier
            .testTag("BatteryCard")
    ) {
        val cornerShape = MaterialTheme.shapes.large

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(cornerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .drawWithContent {
                    drawContent()
                    val widthCutoff = size.width * energy.toFloat()
                    clipPath(Path().apply {
                        addRoundRect(
                            RoundRect(
                                0f,
                                0f,
                                widthCutoff,
                                size.height,
                                cornerShape.toCornerRadius(this@drawWithContent)
                            )
                        )
                    }) {
                        drawRect(
                            brush = Brush.horizontalGradient(gradientColors),
                            size = Size(widthCutoff, size.height)
                        )
                    }
                }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!adjustingEnergy.value) {

                ActionButton(
                    onClick = { viewModel.storeValidatedEnergyLevel(Validation.Correct, energy) },
                    iconPainter = painterResource(R.drawable.rounded_check_24),
                    actionText = "Correct",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationCorrectButton")
                )
                ActionButton(
                    onClick = { adjustingEnergy.value = true },
                    iconPainter = painterResource(R.drawable.rounded_edit_24px),
                    actionText = "Adjust",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustButton"),
                )
            } else {
                TextButton(
                    onClick = { adjustingEnergy.value = false },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustCancelButton")
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        adjustingEnergy.value = false;
                        viewModel.storeValidatedEnergyLevel(
                            Validation.Adjusted,
                            adjustedEnergy.doubleValue
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustSaveButton")
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    iconPainter: Painter,
    actionText: String,
    modifier: Modifier = Modifier,
) {
    Button(onClick, modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
            )
            Text(actionText)
        }
    }
}

fun Shape.toCornerRadius(drawScope: DrawScope): CornerRadius {
    val outline = this.createOutline(drawScope.size, drawScope.layoutDirection, drawScope)
    return when (outline) {
        // Use top-left corner (they’re usually uniform)
        is Outline.Rounded -> outline.roundRect.bottomLeftCornerRadius
        else -> CornerRadius.Zero
    }
}