package org.htwk.pacing.ui.logo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Floaty(
    amplitudeDp: Float = 6f,
    content: @Composable () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "float")
    val offsetY by infinite.animateFloat(
        initialValue = -amplitudeDp,
        targetValue = amplitudeDp,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        modifier = Modifier.offset(y = offsetY.dp)
    ) {
        content()
    }
}
