package org.htwk.pacing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun BatterieKomponente() {
    Box(
        modifier = Modifier
            .graphicsLayer(rotationZ = 90f)
    ) {
        BatterieInhalt()
    }
}
@Composable
fun BatterieInhalt() {
    val overlap = 18.dp

    Box(
        modifier = Modifier.size(width = 100.dp, height = 200.dp)
    ) {
        // oben
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(20.dp)
                    .border(2.dp, Color.Black, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            )
        }
        // unten
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .dynamicHeightMinus(overlap)
                .offset(y = overlap)
                .border(2.dp, Color.Black, RoundedCornerShape(16.dp))
        )
    }
}


fun Modifier.dynamicHeightMinus(overlap: Dp) = this.then(
    layout { measurable, constraints ->
        val pxOffset = overlap.roundToPx()
        val newHeight = constraints.maxHeight - pxOffset
        val placeable = measurable.measure(
            constraints.copy(maxHeight = newHeight, minHeight = newHeight)
        )
        layout(placeable.width, constraints.maxHeight) {
            placeable.place(0, 0)
        }
    }
)

@Preview
@Composable
fun PreviewBatterieKomponente() {
    BatterieKomponente()
}
