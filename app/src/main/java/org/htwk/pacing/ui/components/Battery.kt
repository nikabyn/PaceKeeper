package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.screens.HomeViewModel

/**
 * Shows the current energy level as a continuous gradient that is cut off at the current energy.
 * Allows the user to accept the current energy prediction as correct
 * or adjust it based on how they feel.
 */
@Composable
fun BatteryCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val latestValidation by viewModel.latestValidatedEnergyLevel.collectAsState()
    val adjustedEnergy = remember { mutableDoubleStateOf(energy) }
    val adjustingEnergy = remember { mutableStateOf(false) }

    LaunchedEffect(latestValidation) {
        adjustedEnergy.doubleValue = when (latestValidation?.validation) {
            Validation.Adjusted -> latestValidation!!.percentage.toDouble()
            else -> energy
        }
    }

    val gradientColors = arrayOf(
        Color(0xFFEC4242), // Red
        Color(0xFFE1C508), // Yellow
        Color(0xFF72D207), // Green
    )

    CardWithTitle(
        title = stringResource(R.string.current_energy),
        modifier = modifier.testTag("BatteryCard")
    ) {
        val cornerShape = MaterialTheme.shapes.large
        val barWidth = remember { mutableIntStateOf(0) }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .onGloballyPositioned { layoutCoordinates ->
                    barWidth.intValue = layoutCoordinates.size.width
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cornerShape)
                    .background(MaterialTheme.colorScheme.surfaceDim)
                    .gradientBackground(
                        currentEnergy = energy,
                        adjustedEnergy = adjustedEnergy.doubleValue,
                        colors = gradientColors,
                        cornerShape = cornerShape,
                    )
                    .pointerInput(adjustingEnergy.value) {
                        if (!adjustingEnergy.value) return@pointerInput

                        val updateAdjustedEnergy = { x: Float ->
                            adjustedEnergy.doubleValue = (x / size.width)
                                .coerceIn(0f, 1f)
                                .toDouble()
                        }

                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            updateAdjustedEnergy(change.position.x)
                        }
                    }
                    .testTag("BatteryBar")
            )

            if (!adjustingEnergy.value) return@Box

            val handleSize = DpSize(24.dp, 32.dp)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (adjustedEnergy.doubleValue * barWidth.intValue - handleSize.width.toPx() / 2).toInt(),
                            0
                        )
                    }
                    .size(handleSize)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .shadow(32.dp)
                    .align(Alignment.CenterStart)
                    .testTag("EnergyDragHandle")
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_drag_indicator_24),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!adjustingEnergy.value) {
                ActionButton(
                    onClick = {
                        adjustedEnergy.doubleValue = energy
                        viewModel.storeValidatedEnergyLevel(Validation.Correct, energy)
                    },
                    iconPainter = painterResource(R.drawable.rounded_check_24),
                    actionText = stringResource(R.string.correct),
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
                    onClick = {
                        adjustingEnergy.value = false
                        adjustedEnergy.doubleValue = energy
                    },
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

fun Modifier.gradientBackground(
    currentEnergy: Double,
    adjustedEnergy: Double,
    colors: Array<Color>,
    cornerShape: Shape,
): Modifier = this.then(
    Modifier.drawBehind {
        val change =
            if (adjustedEnergy > currentEnergy) Change.Positive
            else Change.Negative

        val pathCurrent = pathCurrent(
            drawScope = this,
            currentEnergy = currentEnergy,
            cornerShape = cornerShape,
        )
        val pathAdjusted = pathAdjusted(
            drawScope = this,
            adjustedEnergy = adjustedEnergy,
            change = change,
            cornerShape = cornerShape,
        )

        val pathCutoffCurrent = Path.combine(
            when (change) {
                Change.Negative -> PathOperation.ReverseDifference
                Change.Positive -> PathOperation.Intersect
            },
            pathAdjusted,
            pathCurrent,
        )
        val pathCutoffAdjusted = when (change) {
            Change.Negative -> Path.combine(
                PathOperation.Intersect,
                pathAdjusted,
                pathCurrent,
            )

            Change.Positive -> pathAdjusted
        }

        clipPath(pathCutoffAdjusted) {
            drawRect(
                brush = Brush.horizontalGradient(colors.asList()),
                alpha = 0.2f,
                size = size
            )
        }

        val pathStripes = pathDiagonalStripes(
            size = size,
            stripeWidthPx = 2.dp.toPx(),
            stripeSpacingPx = 12.dp.toPx(),
        )

        clipPath(Path.combine(PathOperation.Intersect, pathCutoffAdjusted, pathStripes)) {
            drawRect(
                brush = Brush.horizontalGradient(colors.asList()),
                size = Size(size.width, size.height)
            )
        }

        clipPath(pathCutoffCurrent) {
            drawRect(
                brush = Brush.horizontalGradient(colors.asList()),
                size = Size(size.width, size.height)
            )
        }
    }
)

fun pathCurrent(
    drawScope: DrawScope,
    currentEnergy: Double,
    cornerShape: Shape,
): Path {
    val widthCurrent = drawScope.size.width * currentEnergy.toFloat()
    val outlineCurrent = cornerShape.createOutline(
        Size(widthCurrent, drawScope.size.height),
        drawScope.layoutDirection,
        drawScope
    )

    return Path().apply { addOutline(outlineCurrent) }
}

fun pathAdjusted(
    drawScope: DrawScope,
    adjustedEnergy: Double,
    change: Change,
    cornerShape: Shape,
): Path {
    val widthAdjusted = drawScope.size.width * when (change) {
        Change.Positive -> adjustedEnergy.toFloat()
        Change.Negative -> 1f - adjustedEnergy.toFloat()
    }
    val offsetAdjusted = Offset(
        when (change) {
            Change.Positive -> 0f
            Change.Negative -> drawScope.size.width - widthAdjusted
        },
        0f
    )
    val outlineAdjusted = cornerShape.createOutline(
        Size(widthAdjusted, drawScope.size.height),
        drawScope.layoutDirection,
        drawScope
    )

    return Path().apply {
        addOutline(outlineAdjusted)
        translate(offsetAdjusted)
    }
}

fun pathDiagonalStripes(
    size: Size,
    stripeWidthPx: Float,
    stripeSpacingPx: Float,
): Path {
    val path = Path()

    val diagonal = kotlin.math.hypot(size.width, size.height)
    var startX = -diagonal
    while (startX < size.width + diagonal) {
        path.moveTo(startX, size.height)
        path.lineTo(startX + stripeWidthPx, size.height)
        path.lineTo(startX + stripeWidthPx - size.height, 0f)
        path.lineTo(startX - size.height, 0f)
        path.close()

        startX += stripeSpacingPx
    }

    return path
}

enum class Change {
    Negative,
    Positive,
}