package org.htwk.pacing.ui.logo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BlinkLogo(
    open: Int,
    closed: Int
) {
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        delay(600)

        repeat(3) {
            blinking = true
            delay(100) // Kurz zu
            blinking = false
            delay(150) // Kurz offen
        }

        delay(1000)

        while (true) {
            delay((2200..4500).random().toLong())
            blinking = true
            delay(120)
            blinking = false
        }
    }

    Image(
        painter = painterResource(if (blinking) closed else open),
        contentDescription = null,
        modifier = Modifier.size(160.dp)
    )
}

@Composable
fun shuffleSmileys(
    open: Int,
    closed: Int
) {
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000)

        while (true) {
            delay((2200..4500).random().toLong())
            blinking = true
            delay((1000..3000).random().toLong())
            blinking = false
        }
    }

    Image(
        painter = painterResource(if (blinking) closed else open),
        contentDescription = null,
        modifier = Modifier.size(160.dp)
    )
}