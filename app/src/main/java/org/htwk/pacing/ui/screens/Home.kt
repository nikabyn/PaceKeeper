package org.htwk.pacing.ui.screens

import org.htwk.pacing.backend.mlmodel.MLModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.components.HeartRatePredictionCard
import org.htwk.pacing.ui.components.Series
import kotlin.math.sin
import kotlin.time.Duration.Companion.hours

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun generateInstants(h: Int): List<kotlinx.datetime.Instant> {
    val now = Clock.System.now();
    return List(h * 6) { i ->
        now - h.hours + (i * 10).minutes;
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(40.dp)) {
            val hr_arr0 = doubleArrayOf(59.01, 57.21, 67.92, 73.21, 56.00, 47.15, 47.79, 47.84, 48.29, 50.16, 50.13, 52.52, 49.48, 48.40, 47.81, 47.72, 49.61, 47.57, 52.25, 51.35, 52.41, 52.53, 51.75, 49.57, 48.76, 48.13, 51.76, 50.27, 50.42, 49.80, 51.78, 48.88, 46.81, 49.32, 46.55, 49.94, 51.52, 50.94, 53.97, 49.72, 49.48, 53.15, 64.97, 76.06, 92.60, 77.23, 74.66, 73.05, 73.31, 73.76, 71.15, 67.41, 69.75, 68.33, 66.79, 64.77, 64.40, 64.62, 61.53, 64.71, 66.21, 81.36, 70.33, 74.78, 75.32, 76.32, 75.92, 66.28, 69.40, 66.86, 70.25, 80.51, 78.51, 75.10, 69.58, 79.72, 76.78, 76.01, 80.26, 76.80, 67.76, 66.95, 65.02, 66.17, 66.79, 66.01, 62.39, 66.71, 63.73, 68.67, 61.34, 63.03, 63.70, 63.16, 62.44, 64.91, 61.45, 62.41, 65.47, 78.88, 72.90, 74.95, 69.29, 70.67, 69.25, 67.65, 65.17, 62.95, 67.31, 62.09, 59.39, 57.64, 62.33, 70.07, 66.67, 60.26, 60.38, 59.22, 57.60, 59.23, 76.89, 113.43, 132.63, 141.64, 132.49, 156.72, 150.88, 131.00, 94.31, 90.34, 89.60, 83.48, 82.73, 82.19, 76.62, 76.59, 70.99, 64.12, 69.29, 67.27, 67.37, 72.99, 65.67, 64.72, 66.99, 69.23, 70.92, 70.07, 69.95, 71.04, 72.70, 74.13, 76.32, 70.54, 68.01, 66.51, 51.45, 51.10, 51.20, 50.91, 50.96, 52.60, 53.36, 55.12, 52.53, 52.16, 52.91, 50.42, 50.53, 51.24, 52.61, 49.49, 51.57, 53.22, 52.81, 51.64, 50.48, 47.82, 49.12, 48.34, 50.70, 51.19, 51.25, 49.65, 51.44, 49.37, 63.62, 66.73, 72.71, 65.54, 67.21, 65.39, 68.03, 69.49, 65.46, 73.95, 74.83, 71.48, 65.53, 66.46, 67.45, 65.20, 69.88, 71.88, 65.32, 73.97, 76.41, 70.86, 69.51, 69.05, 67.84, 69.32, 81.78, 79.73, 103.40, 116.80, 102.86, 86.72, 77.43, 82.16, 79.46, 73.26, 80.41, 87.82, 100.33, 86.85, 77.10, 73.45, 85.48, 97.67, 73.69, 69.41, 77.27, 87.85, 69.07, 69.51, 67.14, 58.76, 56.47, 56.16, 53.66, 55.79, 55.21, 59.25, 58.47, 56.05, 55.18, 55.42, 55.37, 54.81, 54.18, 55.33, 56.16, 52.63, 53.14, 57.41, 73.76, 68.06, 87.93, 89.70, 75.07, 70.68, 67.83, 71.52, 78.68, 81.22, 75.53, 72.35, 66.77, 64.24, 64.00, 65.82, 62.11, 61.80, 61.31, 61.78, 60.52, 60.86, 58.98, 57.80, 57.15, 57.31, 58.25, 57.49, 55.97, 55.86, 62.08, 70.40);
            val hr_arr00 = hr_arr0.map{(it - hr_arr0.min()) / 100}
            val hr_arr1 = hr_arr0.map { it.toFloat() }.toFloatArray()

            val predictions = Series(mutableListOf(), mutableListOf())

            val context = LocalContext.current
            val mlModel = remember { MLModel(context) }

            val predictionsX = remember { mutableStateListOf<Double>() }
            val predictionsY = remember { mutableStateListOf<Double>() }

            LaunchedEffect(Unit) {
                while(true) {
                    predictionsX.clear();
                    predictionsY.clear();
                    val now = Clock.System.now()
                    for (i in 1..30) {
                        val iInSeconds: Duration = i.hours;
                        val before = now - iInSeconds
                        val offset = now.toEpochMilliseconds().toInt() % 1000;
                        predictionsX.add(before.toEpochMilliseconds().toDouble());
                        predictionsY.add(
                            sin((i.toDouble() + offset.toDouble() / 1000) / 5)
                        );
                    }
                    delay(100);
                }
            }

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

            val inputX = generateInstants(48)
            //val inputY = DoubleArray(inputX.size) { i -> sin(i * 0.5) }
            val inputY = hr_arr00;

            println(inputX)
            println(inputY)

            HeartRatePredictionCard(
                series = Series(
                    inputX.map { it.toEpochMilliseconds().toDouble() },
                    inputY.toList(),
                ),
                seriesPredicted = Series(
                    predictionsX,
                    predictionsY),
                minPrediction = 0.1f,
                avgPrediction = 0.35f,
                maxPrediction = 0.4f,
            )
        }
    }
}
