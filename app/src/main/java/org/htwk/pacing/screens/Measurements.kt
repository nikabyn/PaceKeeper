package org.htwk.pacing.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.htwk.pacing.math.remap
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.Series
import kotlin.random.Random


@Composable
fun MeasurementsScreen(modifier: Modifier = Modifier) {
    var series by remember { mutableStateOf(Series(emptyArray(), emptyArray())) }
    val dataPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            val value = remap(Random.nextFloat(), 0f, 1f, 55f, 107f)
            time += 1f

            dataPoints.add(Pair(time, value))
            if (dataPoints.size > 50) {
                dataPoints.removeAt(0)
            }

            val values = dataPoints.map { it.second }.toTypedArray()
            val times = dataPoints.map { it.first }.toTypedArray()

            series = Series(values, times)
        }
    }

    Box(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            GraphCard(
                title = "Heart Rate [bpm]",
                series = series,
                yRange = 0f..120f
            )
            GraphCard(
                title = "Heart Rate [bpm], Dynamic Range",
                series = series,
            )
        }
    }
}
