package org.htwk.pacing.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.Series
import kotlin.time.Duration.Companion.hours

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(40.dp)) {
            val now = Clock.System.now()
            EnergyPredictionCard(
                series = Series(
                    listOf(
                        now - 12.hours,
                        now - 11.hours,
                        now - 10.hours,
                        now - 6.hours,
                        now - 4.hours,
                        now - 2.hours,
                        now
                    ).map { it.toEpochMilliseconds().toDouble() },
                    listOf(0.8, 0.82, 0.7, 0.65, 0.67, 0.45, 0.4),
                ),
                minPrediction = 0.1f,
                avgPrediction = 0.35f,
                maxPrediction = 0.4f,
            )
        }
    }
}
