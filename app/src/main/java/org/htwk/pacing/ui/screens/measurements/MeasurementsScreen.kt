package org.htwk.pacing.ui.screens.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.ui.Route
import org.htwk.pacing.ui.components.DemoBanner
import org.htwk.pacing.ui.components.ModeViewModel
import org.htwk.pacing.ui.screens.measurements.Measurement.Distance
import org.htwk.pacing.ui.screens.measurements.Measurement.ElevationGained
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRate
import org.htwk.pacing.ui.screens.measurements.Measurement.HeartRateVariabilityRmssd
import org.htwk.pacing.ui.screens.measurements.Measurement.MenstruationPeriod
import org.htwk.pacing.ui.screens.measurements.Measurement.OxygenSaturation
import org.htwk.pacing.ui.screens.measurements.Measurement.SkinTemperature
import org.htwk.pacing.ui.screens.measurements.Measurement.Sleep
import org.htwk.pacing.ui.screens.measurements.Measurement.Speed
import org.htwk.pacing.ui.screens.measurements.Measurement.Steps
import org.htwk.pacing.ui.screens.measurements.Measurement.Symptoms
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.minutes

/**
 * Top-level screen composable that displays today’s measurements grouped by category.
 *
 * It periodically refreshes today’s measurements from the [MeasurementsViewModel]
 * and renders a scrollable list of measurement cards. Each card shows a summary
 * statistic and a small graph preview, and navigates to a detailed screen when tapped.
 *
 * @param navController Used to navigate to individual measurement detail screens.
 * @param modifier Optional [Modifier] for styling and layout.
 * @param viewModel Provides access to measurement data for today.
 */
@Composable
fun MeasurementsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
    modeViewModel: ModeViewModel = koinViewModel()
) {
    var measurements by remember { mutableStateOf(viewModel.initialMeasurementsToday()) }

    LaunchedEffect(Unit) {
        while (true) {
            measurements = viewModel.measurementsToday()
            delay(1.minutes)
        }
    }

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column {
            DemoBanner(modeViewModel = modeViewModel)

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
                modifier = Modifier.padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.extraLarge
                )
            ) {
                MeasurementsCategory(stringResource(R.string.activity))
                MeasurementsCard(navController, Steps, measurements)
                MeasurementsCard(navController, Distance, measurements)
                MeasurementsCard(navController, ElevationGained, measurements)
                MeasurementsCard(navController, Speed, measurements)

                Spacer(Modifier.height(Spacing.medium))
                MeasurementsCategory(stringResource(R.string.health))
                MeasurementsCard(navController, HeartRate, measurements)
                MeasurementsCard(navController, Sleep, measurements)
                MeasurementsCard(navController, Symptoms, measurements)
                MeasurementsCard(navController, MenstruationPeriod, measurements)
                MeasurementsCard(navController, OxygenSaturation, measurements)
                MeasurementsCard(navController, HeartRateVariabilityRmssd, measurements)
                MeasurementsCard(navController, SkinTemperature, measurements)
            }
        }
    }
}

/**
 * Displays a category header for a group of measurements.
 *
 * Used to visually separate measurement cards into logical sections
 * such as "Activity" or "Health".
 *
 * @param name The display name of the category.
 */
@Composable
private fun MeasurementsCategory(name: String) =
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(start = Spacing.medium),
    )

/**
 * A clickable card that summarizes a single measurement.
 *
 * The card shows the measurement title, aggregated statistics for today,
 * and a compact graph preview. Clicking the card navigates to the
 * detailed measurement screen.
 *
 * @param navController Used to navigate to the measurement detail screen.
 * @param measurement The measurement type represented by this card.
 * @param measurements A map of all measurements and their timed entries for today.
 */
@Composable
private fun MeasurementsCard(
    navController: NavController,
    measurement: Measurement,
    measurements: Map<Measurement, List<TimedEntry>>,
) {
    val entries = measurements[measurement].orEmpty()
    val range = remember { TimeRange.today() }

    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        onClick = { navController.navigate(Route.measurement(measurement)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("MeasurementsCard")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
                .fillMaxSize(),
        ) {
            Text(
                text = measurement.title(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("MeasurementsCardTitle"),
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            Row {
                TitleAndStats(
                    measurement,
                    entries,
                    range,
                    modifier = Modifier
                        .weight(1f)
                )
                TinyGraphPreview(
                    measurement,
                    entries,
                    range,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                )
            }
        }
    }
}

class MeasurementsViewModel(private val db: PacingDatabase) : ViewModel() {
    fun initialMeasurementsToday() = Measurement.entries.associateWith {
        emptyList<TimedEntry>()
    }

    suspend fun measurementsToday() = Measurement.entries.associateWith { measurement ->
        measurement
            .dao(db)
            .getInRange(TimeRange.today().start, TimeRange.today().end)
            .let { measurement.processPreview(it) }
    }
}