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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import org.htwk.pacing.randomHeartRate
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.Series

@Composable
fun MeasurementsScreen(modifier: Modifier = Modifier) {
    var start = Clock.System.now()
    var values = remember { mutableStateListOf<Float>() }
    var times = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        randomHeartRate().collect { (value, time) ->
            values.add(value)
            times.add((time - start).inWholeMilliseconds.toFloat())

            if (values.size > 50) {
                values.removeAt(0);
                times.removeAt(0);
            }
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
                series = Series(values.toTypedArray(), times.toTypedArray()),
                yRange = 0f..120f,
                xSteps = 2u,
            )
            GraphCard(
                title = "Heart Rate [bpm], Dynamic Range",
                series = Series(values.toTypedArray(), times.toTypedArray()),
                xSteps = 2u,
            )
        }
    }
}
