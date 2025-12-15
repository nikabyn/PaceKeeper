package org.htwk.pacing.ui.logo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

@Composable
fun ElasticPopEntry(
    delay: Long = 0,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (delay > 0) kotlinx.coroutines.delay(delay)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}

@Composable
fun SoftEntry(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current

    val startOffsetPx = with(density) { 50.dp.toPx() }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val translationY = startOffsetPx * (1 - progress.value)
    val scale = 0.9f + (0.1f * progress.value)

    Box(
        modifier = modifier.graphicsLayer {
            this.translationY = translationY
            this.alpha = progress.value // Fade In
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}

@Composable
fun DelayedFadeIn(
    delayMillis: Long,
    durationMillis: Int = 800,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis)) +
                slideInVertically(
                    initialOffsetY = { 40 }, // Kommt leicht von unten
                    animationSpec = tween(durationMillis)
                ),
        modifier = modifier
    ) {
        content()
    }
}