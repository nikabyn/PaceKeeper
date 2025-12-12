package org.htwk.pacing.ui.logo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun RollingEntry(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationProgress = remember { Animatable(0f) }

    val density = LocalDensity.current
    val startOffsetPx = with(density) { -200.dp.toPx() }

    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy, // Ein bisschen Bouncen am Ende
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val translationX = startOffsetPx * (1 - animationProgress.value)
    val rotation = -360f * (1 - animationProgress.value) // Eine volle Drehung

    Box(
        modifier = modifier.graphicsLayer {
            this.translationX = translationX
            this.rotationZ = rotation
            this.alpha = animationProgress.value.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}