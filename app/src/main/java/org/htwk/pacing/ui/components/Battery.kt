package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R

/**
 * Shows the current energy level using 1 to 6 colored bars (a battery).
 */
@Composable
fun BatteryCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    modifier: Modifier = Modifier,
) {
    val inactiveColor = if (isSystemInDarkTheme()) {
        lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.2f)
    } else {
        lerp(MaterialTheme.colorScheme.surfaceVariant, Color.Black, 0.15f)
    }

    val gradientColors = arrayListOf(
        Color(0xFFEC4242), // Red
        Color(0xFFE1C508), // Yellow
        Color(0xFF72D207), // Green
    )

    CardWithTitle(
        title = stringResource(R.string.current_energy),
        modifier = modifier
            .height(200.dp)
            .testTag("BatteryCard")
    ) {
        val cornerShape = MaterialTheme.shapes.large

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.5f)
                .clip(cornerShape)
                .background(inactiveColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, cornerShape)
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