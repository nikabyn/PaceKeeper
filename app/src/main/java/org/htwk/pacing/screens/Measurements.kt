package org.htwk.pacing.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.Series


@Composable
fun MeasurementsScreen(modifier: Modifier = Modifier) {
    Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            GraphCard(
                title = "Title 1", Series(
                    arrayOf(0.0f, 0.6f, 0.2f, 1.0f),
                    arrayOf(0.0f, 0.2f, 0.6f, 1.0f),
                )
            )
            GraphCard(
                title = "Title 2", Series(
                    arrayOf(60.0f, 80.0f, 65.0f, 60.0f),
                    arrayOf(0.0f, 0.2f, 0.6f, 1.0f),
                )
            )
            GraphCard(
                title = "Title 3", Series(
                    arrayOf(0.0f, 20.0f, 10.0f),
                    arrayOf(0.0f, 12.0f, 24.0f),
                )
            )
            GraphCard(
                title = "Title 4", Series(
                    arrayOf(0.0f, 20.0f, 10.0f),
                    arrayOf(12.0f, 16.0f, 24.0f),
                )
            )
            GraphCard(
                title = "Title 2",
                series = Series(
                    arrayOf(60.0f, 80.0f, 65.0f, 60.0f),
                    arrayOf(12.0f, 14.0f, 18.0f, 24.0f),
                ),
                xRange = 0.0f..24.0f,
                yRange = 0.0f..100.0f,
            )
        }
    }
}
