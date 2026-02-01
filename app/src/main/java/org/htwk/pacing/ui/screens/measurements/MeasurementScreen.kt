package org.htwk.pacing.ui.screens.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.backend.database.TimedSeries
import org.htwk.pacing.backend.database.UserProfileEntry
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.components.CardWithTitle
import org.htwk.pacing.ui.components.drawHeartRateGraph
import org.htwk.pacing.ui.components.graph.AxisLabel
import org.htwk.pacing.ui.components.graph.GraphCanvas
import org.htwk.pacing.ui.components.graph.GraphLayout
import org.htwk.pacing.ui.components.graph.drawLines
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.htwk.pacing.ui.theme.extendedColors
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    navController: NavController,
    measurement: Measurement,
    viewModel: MeasurementViewModel = MeasurementViewModel(measurement, koinInject()),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var expandedRange by remember { mutableStateOf(TimeRange.today()) }
    var expandedEntries by remember { mutableStateOf<List<TimedEntry>>(emptyList()) }

    AnimatedContent(
        targetState = expanded,
        label = "GraphTransition"
    ) { isExpanded ->
        if (isExpanded) {
            FullscreenGraphOverlay(
                measurement,
                expandedEntries,
                expandedRange,
                onDismiss = { expanded = false },
                viewModel,
            )
        } else {
            DefaultScreen(
                navController,
                measurement,
                viewModel,
                toggleOverlay = { entries, range ->
                    expanded = true
                    expandedRange = range
                    expandedEntries = entries
                },
            )
        }
    }
}

@Composable
private fun DefaultScreen(
    navController: NavController,
    measurement: Measurement,
    viewModel: MeasurementViewModel,
    toggleOverlay: (entries: List<TimedEntry>, range: TimeRange) -> Unit,
) {
    val entriesToday by viewModel.entriesToday.collectAsState()
    val rangeToday = remember(entriesToday) { TimeRange.today() }
    val lazyPagingItems = viewModel.pagedItems.collectAsLazyPagingItems()

    SubScreen(
        title = measurement.title(),
        onBack = { navController.popBackStack() }
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.largeIncreased),
                modifier = Modifier
                    .padding(horizontal = Spacing.large)
                    .fillMaxSize()
            ) {
                item {
                    LargeGraphPreview(
                        measurement,
                        entriesToday,
                        onClick = {
                            toggleOverlay(
                                entriesToday,
                                rangeToday
                            )
                        },
                        viewModel,
                        modifier = Modifier.height(350.dp)
                    )
                }

                items(
                    lazyPagingItems.itemCount,
                    key = lazyPagingItems.itemKey { it.key }
                ) { index ->
                    val item = lazyPagingItems[index] ?: return@items
                    PreviewCard(measurement, item, onClick = {
                        toggleOverlay(
                            item.entries,
                            item.range,
                        )
                    })
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(measurement: Measurement, item: ListItem, onClick: () -> Unit) = Card(
    colors = CardStyle.colors,
    shape = CardStyle.shape,
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        Modifier.padding(
            horizontal = Spacing.large,
            vertical = Spacing.largeIncreased
        )
    ) {
        TitleAndStats(
            measurement,
            item.entries,
            item.range,
            modifier = Modifier.weight(1f)
        )
        TinyGraphPreview(
            measurement,
            item.entries,
            item.range,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargeGraphPreview(
    measurement: Measurement,
    entries: List<TimedEntry>,
    onClick: () -> Unit,
    viewModel: MeasurementViewModel,
    modifier: Modifier = Modifier,
) {
    val entriesPreview = remember(entries) {
        measurement.processPreview(entries)
    }
    val yDataToday = remember(entriesPreview) {
        entriesPreview.fastMap { measurement.entryToYValue(it) }
    }
    val yRange = measurement.yRange(yDataToday)
    val ySteps = measurement.ySteps(yRange)
    val xRange = TimeRange.today()

    val colorLine = MaterialTheme.colorScheme.primary
    val colorGridLine = MaterialTheme.colorScheme.outlineVariant

    val colorAwake = MaterialTheme.colorScheme.error
    val colorREM = MaterialTheme.colorScheme.tertiary
    val colorLightSleep = MaterialTheme.colorScheme.primary
    val colorDeepSleep = MaterialTheme.colorScheme.secondary

    val zoneColors = arrayOf(
        MaterialTheme.extendedColors.green, // green: healthZone
        MaterialTheme.extendedColors.cyan, // cyan: recoveryZone
        MaterialTheme.extendedColors.yellow, // yellow: exertionZone
        MaterialTheme.extendedColors.red  // red: area above threshold
    )
    val zonesResult by viewModel.heartRateZones.collectAsState()

    CardWithTitle(
        title = stringResource(R.string.today),
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("GraphPreviewCard")
    ) {
        GraphLayout(
            xLabels = {
                for (i in 0..24 step 4) {
                    AxisLabel(i.toString())
                }
            },
            yLabels = {
                ySteps.fastForEachReversed { (value, label) ->
                    AxisLabel(label)
                }
            }
        ) {
            GraphCanvas(
                Modifier
                    .padding(Spacing.small)
                    .drawLines(ySteps.size, colorGridLine)
            ) {
                drawMeasurement(
                    measurement,
                    entriesPreview,
                    xRange,
                    drawLine = { measurement, entries, range ->
                        drawLine(measurement, entries, range, colorLine)
                    },
                    drawSleep = { entries, range ->
                        drawSleepPreview(
                            entries,
                            range,
                            colorAwake,
                            colorREM,
                            colorLightSleep,
                            colorDeepSleep,
                        )
                    },
                    drawHeartRate = { entries, range ->
                        drawHeartRateGraph(
                            entries,
                            range,
                            zoneColors,
                            zonesResult,
                            colorLine,
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenGraphOverlay(
    measurement: Measurement,
    entries: List<TimedEntry>,
    range: TimeRange,
    onDismiss: () -> Unit,
    viewModel: MeasurementViewModel,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoneRanges by viewModel.heartRateZones.collectAsState()

    val yDataToday = entries.fastMap { measurement.entryToYValue(it) }
    val yRange = measurement.yRange(yDataToday)
    val ySteps = measurement.ySteps(yRange)

    val colorLine = MaterialTheme.colorScheme.primary
    val colorGridLine = MaterialTheme.colorScheme.outlineVariant

    val colorAwake = MaterialTheme.colorScheme.error
    val colorREM = MaterialTheme.colorScheme.tertiary
    val colorLightSleep = MaterialTheme.colorScheme.primary
    val colorDeepSleep = MaterialTheme.colorScheme.secondary

    val zoneColors = arrayOf(
        MaterialTheme.extendedColors.green, // green: healthZone
        MaterialTheme.extendedColors.cyan, // cyan: recoveryZone
        MaterialTheme.extendedColors.yellow, // yellow: exertionZone
        MaterialTheme.extendedColors.red  // red: area above threshold
    )

    SubScreen(
        title = measurement.title(),
        onBack = onDismiss
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = offset.copy(x = offset.x + pan.x)
                    }
                }
        ) {
            GraphLayout(
                xLabels = {
                    for (i in 0..24 step 4) {
                        AxisLabel(i.toString())
                    }
                },
                yLabels = {
                    ySteps.fastForEachReversed { (value, label) ->
                        AxisLabel(label)
                    }
                }
            ) {
                GraphCanvas(
                    Modifier
                        .padding(Spacing.small)
                        .drawLines(ySteps.size, colorGridLine)
                ) {
                    drawMeasurement(
                        measurement,
                        entries,
                        range,
                        drawLine = { measurement, entries, range ->
                            drawLine(measurement, entries, range, colorLine)
                        },
                        drawSleep = { entries, range ->
                            drawSleepPreview(
                                entries,
                                range,
                                colorAwake,
                                colorREM,
                                colorLightSleep,
                                colorDeepSleep,
                            )
                        },
                        drawHeartRate = { entries, range ->
                            drawHeartRateGraph(
                                entries,
                                range,
                                zoneColors,
                                zoneRanges,
                                colorLine,
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.rounded_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            )
        },
        content = content
    )
}

class MeasurementViewModel(val measurement: Measurement, val db: PacingDatabase) : ViewModel() {
    private val dao = measurement.dao(db)

    val heartRateZones = db.userProfileDao()
        .getProfileLive()
        .map {
            val birthYear = it?.birthYear ?: 1990
            val currentYear = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
            val age = currentYear - birthYear

            val sex = when (it?.sex ?: UserProfileEntry.Sex.UNSPECIFIED) {
                UserProfileEntry.Sex.MALE -> HeartRateZones.Sex.MALE
                UserProfileEntry.Sex.UNSPECIFIED,
                UserProfileEntry.Sex.OTHER,
                UserProfileEntry.Sex.FEMALE -> HeartRateZones.Sex.FEMALE
            }

            val restingHeartRate = it?.restingHeartRateBpm ?: 60

            HeartRateZones.calculateZones(
                HeartRateZones.HeartRateInput(age, sex, restingHeartRate)
            )
        }.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeartRateZones.calculateZones(
                HeartRateZones.HeartRateInput(30, HeartRateZones.Sex.FEMALE, 60)
            )
        )

    val entriesToday = dao.getChangeTrigger()
        .map {
            val today = TimeRange.today()
            dao.getInRange(today.start, today.end)
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pagedItems = Pager(PagingConfig(pageSize = 2)) {
        TimedEntryPagingSource(measurement, dao)
    }.flow.cachedIn(viewModelScope)
}

class TimedEntryPagingSource(
    val measurement: Measurement,
    val dao: TimedSeries<out TimedEntry>,
    var currentKey: Long = 111
) :
    PagingSource<Int, ListItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ListItem> {
        return try {
            val page = params.key ?: 0

            // Load a page of data
            val pageSize = params.loadSize
            val response = List(pageSize) { i ->
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val date = today.date.minus(page * pageSize + i, unit = DateTimeUnit.DAY)
                val start = date.atStartOfDayIn(TimeZone.currentSystemDefault())
                val end = start + 24.hours
                val range = TimeRange(start, end)
                val entries = measurement.processPreview(dao.getInRange(start, end))
                ListItem(currentKey++, range, entries)
            }

            val nextKey = if (response.isEmpty()) null else page + 1
            val prevKey = if (page == 0) null else page - 1

            LoadResult.Page(
                data = response,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ListItem>): Int? {
        // Try to anchor around the currently visible items
        return state.anchorPosition?.let { anchorPos ->
            val page = state.closestPageToPosition(anchorPos)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }
}

data class ListItem(
    val key: Long,
    val range: TimeRange,
    val entries: List<TimedEntry>,
)