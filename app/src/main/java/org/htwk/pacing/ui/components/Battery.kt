package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.htwk.pacing.R
import org.htwk.pacing.ui.math.remap
import kotlin.math.ceil

/**
 * Shows the current energy level using 1 to 6 colored bars (a battery).
 */
@Composable
fun BatteryCard(
    @FloatRange(from = 0.0, to = 1.0) energy: Double,
    modifier: Modifier = Modifier,
) {
    fun lerpThreeColors(color1: Color, color2: Color, color3: Color): Color = when {
        energy <= 0.5 -> {
            val t = remap(energy, 0.0, 0.5, 0.0, 1.0).toFloat()
            lerp(color1, color2, t)
        }

        else -> {
            val t = remap(energy, 0.5, 1.0, 0.0, 1.0).toFloat()
            lerp(color2, color3, t)
        }
    }

    val red = Color(0xFFEC4242)
    val yellow = Color(0xFFE1C508)
    val green = Color(0xFF72D207)
    val color = lerpThreeColors(red, yellow, green)

    val inactiveOutlineColor = MaterialTheme.colorScheme.outline
    val inactiveBackgroundColor =
        if (isSystemInDarkTheme()) {
            lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.2f)
        } else {
            lerp(MaterialTheme.colorScheme.surfaceVariant, Color.Black, 0.15f)
        }

    CardWithTitle(
        stringResource(R.string.current_energy), modifier = modifier
            .height(200.dp)
            .testTag("BatteryCard")
    ) {
        when {
            energy < 0.4 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red, shape = RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠️ Low Energy",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            energy < 0.7 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFA500), shape = RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Medium Energy",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "High Energy",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val numActiveBars = if (energy == 0.0) {
                1
            } else {
                ceil(energy * 6.0).toInt()
            }

            for (i in 0..5) {
                val shape = RoundedCornerShape(8.dp)
                val isActive = i < numActiveBars
                val outlineColor = if (isActive) color else inactiveOutlineColor
                val backgroundColor = if (isActive) {
                    color.copy(alpha = 0.4f)
                } else {
                    inactiveBackgroundColor
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(backgroundColor)
                        .border(width = 1.dp, color = outlineColor, shape = shape)
                        .testTag("BatteryCardBar")
                ) {}
            }
        }
    }
}