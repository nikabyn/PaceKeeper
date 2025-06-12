package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
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
import kotlin.random.Random
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

val SegmentColorKey = SemanticsPropertyKey<Color>("segmentColor")
var SemanticsPropertyReceiver.segmentColor by SegmentColorKey



fun BalkenPerEnergie(): List<Int> { //errechnet wie viele Balken geleert werden müssen
    var Energiebalken = listOf<Int>()

    var x: Int = Random.nextInt(0, 100)
    //Platzhalter  für den Energiepegel des Nutzers
    //muss später ersetzt/importiert werden, wenn die Energie berechnet wird

    when (x){
        in 84..100 -> Energiebalken = listOf()
        in 68..83 -> Energiebalken = listOf(0)
        in 51..67 -> Energiebalken = listOf(0, 1)
        in 34..50 -> Energiebalken = listOf(0, 1, 2)
        in 17..33 -> Energiebalken = listOf(0, 1, 2, 3)
        in 1..16 -> Energiebalken = listOf(0, 1, 2, 3, 4)
           0 -> Energiebalken = listOf(0, 1, 2, 3, 4, 5)
    }

    return Energiebalken
}

@Composable
fun BatterieKomponente() {

 val balkenStand = BalkenPerEnergie()

    val colors = createBatteryColors(6, overrideIndices = balkenStand)

    Box(
        modifier = Modifier
            .graphicsLayer(rotationZ = 90f)
    ) {
        BatterieInhalt(segmentColors = colors)
    }
}


@Composable
fun BatterieInhalt(segmentColors: List<Color> = listOf()) {
    val maxSegments = 6
    val overlap = 18.dp

    // Fallback-Farben (wenn nichts übergeben wurde)
    val defaultColors = listOf(
        Color.Green, Color.Green,
        Color.Yellow, Color.Yellow,
        Color.Red, Color.Red
    )

    val farben = if (segmentColors.isNotEmpty()) segmentColors else defaultColors


    Box(
        modifier = Modifier.size(width = 100.dp, height = 200.dp)
    ) {
        // Körper mit Segmenten
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
        // Kopf
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .dynamicHeightMinus(overlap)
                .offset(y = overlap)
                .border(2.dp, Color.Black, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (i in 0 until maxSegments) {
                    val segmentColor = if (i < farben.size) farben[i] else Color.White
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .border(1.dp, Color.Black, RoundedCornerShape(7.dp))
                            .background(segmentColor, RoundedCornerShape(7.dp))
                            .testTag("segment_$i")
                            .semantics { this.segmentColor = segmentColor }
                    )
                }
            }
        }
    }
}

fun createBatteryColors(segments: Int, overrideIndices: List<Int> = emptyList()): List<Color> {
    val baseColors = listOf(
        Color.Green, Color.Green,
        Color.Yellow, Color.Yellow,
        Color.Red, Color.Red
    )
    return baseColors.mapIndexed { index, color ->
        if (index in overrideIndices) Color.White else color
    }.take(segments)
}


/*

@Preview
@Composable
fun Balkentest(){
    Box(modifier = Modifier.graphicsLayer(rotationZ = 90f))
    {
        BatterieInhalt(visibleSegments = 2)
    }
}


@Preview
@Composable
fun PreviewBatterieKomponente() {
    BatterieKomponente()
}

*/
