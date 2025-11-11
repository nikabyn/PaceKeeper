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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
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
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.screens.HomeViewModel

/**
 * Displays a card representing the user's current energy level with interactive validation.
 *
 * The card shows a horizontal energy bar with a gradient corresponding to the current energy
 * level. Users can either accept the predicted energy as correct or adjust it to reflect how
 * they actually feel.
 *
 * Features:
 * - Shows the current energy as a continuous gradient.
 * - Makes the bar draggable to adjust energy levels when in editing mode.
 * - Synchronizes with the [HomeViewModel] to persist user-validated energy levels.
 *
 * @param energy The initial predicted energy level (0.0..=1.0) to display in the bar.
 * @param viewModel The [HomeViewModel] instance used to read and store validated energy levels.
 * @param modifier Optional [Modifier] for layout, styling, or testing.
 */
@Composable
fun BatteryCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current

    val latestValidation by viewModel.latestValidatedEnergyLevel.collectAsState()
    val adjustedEnergy = remember { mutableDoubleStateOf(energy) }
    val adjustingEnergy = remember { mutableStateOf(false) }

    LaunchedEffect(latestValidation) {
        adjustedEnergy.doubleValue = when (latestValidation?.validation) {
            Validation.Adjusted -> latestValidation!!.percentage.toDouble()
            else -> energy
        }
    }

    var previousAdjustedEnergy = 0.0
    val showSnackbar: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
        }
    }
    val onCorrect = {
        adjustedEnergy.doubleValue = energy
        viewModel.storeValidatedEnergyLevel(Validation.Correct, energy)
        showSnackbar(localContext.getString(R.string.current_energy_saved))
    }
    val onAdjust = {
        previousAdjustedEnergy = adjustedEnergy.doubleValue
        adjustingEnergy.value = true
    }
    val onCancel = {
        adjustingEnergy.value = false
        adjustedEnergy.doubleValue = previousAdjustedEnergy
    }
    val onSave = {
        adjustingEnergy.value = false;
        viewModel.storeValidatedEnergyLevel(Validation.Adjusted, adjustedEnergy.doubleValue)
        showSnackbar(localContext.getString(R.string.adjusted_energy_saved))
    }

    CardWithTitle(
        title = stringResource(R.string.current_energy),
        modifier = modifier.testTag("BatteryCard")
    ) {
        EnergyBar(
            currentEnergy = energy,
            adjustedEnergy = adjustedEnergy.doubleValue,
            adjusting = adjustingEnergy.value,
            onAdjust = { adjustedEnergy.doubleValue = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!adjustingEnergy.value) {
                ActionButton(
                    onClick = onCorrect,
                    iconPainter = painterResource(R.drawable.rounded_check_24),
                    actionText = stringResource(R.string.correct),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationCorrectButton")
                )
                ActionButton(
                    onClick = onAdjust,
                    iconPainter = painterResource(R.drawable.rounded_edit_24px),
                    actionText = "Adjust",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustButton"),
                )
            } else {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustCancelButton")
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ValidationAdjustSaveButton")
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/**
 * Displays a horizontal energy bar with a gradient representing the current energy level
 * and an overlay for the (user) adjusted energy level.
 *
 * Features:
 * - Shows the current energy as a continuous gradient.
 * - Allows the user to adjust the energy level by dragging horizontally while `adjusting` is true.
 * - Automatically clamps adjustments between 0.0 and 1.0.
 * - Includes a draggable handle that visually aligns with the adjusted energy level.
 *
 * @param currentEnergy The baseline energy level (0.0..=1.0) to display in the gradient.
 * @param adjustedEnergy The user-adjustable energy level (0.0..=1.0) that the overlay/handle represents.
 * @param adjusting Whether the user is currently allowed to adjust the energy level.
 * @param onAdjust Callback invoked whenever the user drags the bar to update the energy.
 */
@Composable
private fun EnergyBar(
    currentEnergy: Double,
    adjustedEnergy: Double,
    adjusting: Boolean,
    onAdjust: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradientColors = arrayOf(
        Color(0xFFEC4242), // Red
        Color(0xFFE1C508), // Yellow
        Color(0xFF72D207), // Green
    )

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
                .pointerInput(adjusting) {
                    if (!adjusting) return@pointerInput

                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val updatedEnergyLevel =
                            (change.position.x / size.width).coerceIn(0f, 1f).toDouble()
                        onAdjust(updatedEnergyLevel)
                    }
                }
                .clip(cornerShape)
                .background(MaterialTheme.colorScheme.surfaceDim)
                .gradientBars(
                    currentEnergy = currentEnergy,
                    adjustedEnergy = adjustedEnergy,
                    colors = gradientColors,
                    cornerShape = cornerShape,
                )
                .testTag("BatteryBar")
        )

        if (!adjusting) return@Box

        val handleSize = DpSize(24.dp, 32.dp)
        val handleShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .offset {
                    // Always center handle on the end of the adjusted energy level bar
                    IntOffset(
                        (adjustedEnergy * barWidth.intValue - handleSize.width.toPx() / 2).toInt(),
                        0
                    )
                }
                .size(handleSize)
                .dropShadow(
                    shape = handleShape,
                    shadow = Shadow(15.dp, spread = 2.dp, alpha = 0.15f),
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = handleShape
                )
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
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    iconPainter: Painter,
    actionText: String,
    modifier: Modifier = Modifier,
) {
    Button(onClick, modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
            )
            Text(actionText)
        }
    }
}

/**
 * Draws a energy comparison bar with gradient fill and diagonal stripe overlay.
 *
 * This modifier visualizes the difference between `currentEnergy` and `adjustedEnergy`:
 * - The base (current) energy is filled with a gradient.
 * - The changed portion is highlighted with translucent gradient stripes.
 * - Positive and negative changes are handled with opposite clipping logic.
 *
 * @param currentEnergy The original energy level (0.0..=1.0).
 * @param adjustedEnergy The updated energy level (0.0..=1.0) after adjustment.
 * @param colors The gradient colors used for both the base and highlight regions.
 * @param cornerShape The shape used to define the bar corners.
 */
private fun Modifier.gradientBars(
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

        // Calculate overlapping and difference paths for clipping regions
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

        // Draw translucent overlay for the adjusted region
        clipPath(pathCutoffAdjusted) {
            drawRect(
                brush = Brush.horizontalGradient(colors.asList()),
                alpha = 0.2f,
                size = size
            )
        }

        // Draw diagonal stripes over the translucent region
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

        // Draw the solid gradient fill for the current energy level
        clipPath(pathCutoffCurrent) {
            drawRect(
                brush = Brush.horizontalGradient(colors.asList()),
                size = Size(size.width, size.height)
            )
        }
    }
)

/**
 * Builds a [Path] representing the filled area for the current energy value.
 *
 * @param drawScope The current [DrawScope] in which the drawing occurs.
 * @param currentEnergy The value of current energy (0.0..=1.0).
 * @param cornerShape The shape defining the corners of the bar.
 * @return A [Path] covering the current energy portion of the bar.
 */
private fun pathCurrent(
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

/**
 * Builds a [Path] representing the adjusted energy level area.
 *
 * Depending on the direction of change, the path is offset so that
 * positive changes grow rightward and negative changes shrink leftward.
 *
 * @param drawScope The current [DrawScope].
 * @param adjustedEnergy The adjusted energy level (0.0..=1.0).
 * @param change Whether the energy increased or decreased.
 * @param cornerShape The shape defining the corners of the bar.
 *
 * @return A [Path] representing the adjusted energy region.
 */
private fun pathAdjusted(
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

/**
 * Generates a diagonal stripe pattern [Path] used for overlaying texture effects.
 *
 * The stripes are drawn at an angle across the component and will be
 * combined with another [Path] via [PathOperation.Intersect] for masking.
 *
 * @param size The total drawable size.
 * @param stripeWidthPx The width of each stripe in pixels.
 * @param stripeSpacingPx The horizontal spacing between consecutive stripes in pixels.
 *
 * @return A [Path] containing repeating diagonal stripe shapes.
 */
private fun pathDiagonalStripes(
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

private enum class Change {
    Negative,
    Positive,
}