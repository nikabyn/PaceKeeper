package org.htwk.pacing.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.UserProfileDao
import org.htwk.pacing.ui.components.EnergyPredictionCard
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

/**
 * Welcome and introduction to the most important components of the app.
 * Screen is shown when opening the app for the very first time and reviewable in the settings.
 */

@Composable
fun WelcomeScreen(onFinished: () -> Unit, viewModel: WelcomeViewModel = koinViewModel()) {
    val numPages = 4
    val pagerState = rememberPagerState(pageCount = { numPages })
    val isLastPage = pagerState.currentPage == numPages - 1
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Scaffold { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.large, vertical = Spacing.extraLarge)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("pager")
                ) { pageIndex ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (pageIndex) {
                            0 -> WelcomePage()
                            1 -> PredictionPage()
                            2 -> SymptomPage()
                            3 -> DataUsagePage(viewModel)
                        }
                    }
                }

                NavigationBar(
                    pagerState,
                    numPages,
                    canContinue = when (isLastPage) {
                        true -> viewModel.termsAccepted
                        false -> true
                    },
                    onForwards = {
                        if (isLastPage && viewModel.termsAccepted) {
                            onFinished()
                            return@NavigationBar
                        }
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    onBackwards = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationBar(
    pagerState: PagerState,
    numPages: Int,
    canContinue: Boolean,
    onForwards: () -> Unit,
    onBackwards: () -> Unit,
) = Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
) {
    AnimatedVisibility(
        visible = pagerState.currentPage > 0,
        modifier = Modifier.align(Alignment.CenterStart),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Button(
            onClick = onBackwards,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier.testTag("nav_back"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back)
            )
        }
    }

    NavigationPoints(
        pagerState.currentPage,
        numPages
    )

    val isLastPage = pagerState.currentPage == numPages - 1
    Button(
        onClick = onForwards,
        enabled = canContinue,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .testTag("nav_forward"),
    ) {
        AnimatedVisibility(visible = isLastPage) {
            Icon(
                Icons.Default.Check,
                "Fertig"
            )
        }
        AnimatedVisibility(visible = !isLastPage) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Weiter"
            )
        }
    }
}

@Composable
private fun NavigationPoints(page: Int, numPages: Int) {
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.testTag("nav_dots")) {
        repeat(numPages) { iteration ->
            val color =
                if (page == iteration) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(color)
                    .size(10.dp),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    RollingEntry {
        AnimateFloating {
            BlinkLogo(
                open = R.drawable.ic_logo_open,
                closed = R.drawable.ic_logo_closed
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Title(stringResource(R.string.title_welcome_page))

    Spacer(modifier = Modifier.height(Spacing.large))

    Description(stringResource(R.string.description_welcome_page))
}

@Composable
private fun PredictionPage() {
    Box(contentAlignment = Alignment.Center) {
        val current = Clock.System.now()
        val start = current - 6.hours
        val data = List(10) {
            val progress = it.toDouble() / 10.0
            val time = start + (current - start) * progress
            PredictedEnergyLevelEntry(
                time,
                percentageNow = Percentage.fromDouble(Random.nextDouble(0.0, 1.0)),
                timeFuture = time + 6.hours,
                percentageFuture = Percentage.fromDouble(Random.nextDouble(0.0, 1.0))
            )
        }

        EnergyPredictionCard(
            data = data,
            currentEnergy = 0.5f,
            minPrediction = 0.0f,
            avgPrediction = 0.6f,
            maxPrediction = 1.0f,
            modifier = Modifier.height(300.dp)
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Title(stringResource(R.string.title_prediction_page))

    Spacer(modifier = Modifier.height(Spacing.large))

    Description(stringResource(R.string.description1_prediction_page))
    Description(stringResource(R.string.description2_prediction_page))
    Description(stringResource(R.string.description3_prediction_page))
}

@Composable
private fun SymptomPage() {
    RollingEntry {
        AnimateFloating {
            ShuffleSmileys(
                R.drawable.very_happy,
                R.drawable.happy,
                R.drawable.sad,
                R.drawable.very_sad
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Title(stringResource(R.string.title_symptom_page))

    Spacer(modifier = Modifier.height(Spacing.large))

    Description(stringResource(R.string.description1_symptom_page))
    Description(stringResource(R.string.description2_symptom_page))
    Description(stringResource(R.string.description3_symptom_page))
}

@Composable
private fun DataUsagePage(viewModel: WelcomeViewModel) {
    val onAcceptedChange = { newVal: Boolean -> viewModel.termsAccepted = newVal }
    Image(
        painter = painterResource(
            if (viewModel.termsAccepted) {
                R.drawable.ic_logo_closed
            } else {
                R.drawable.ic_logo_open
            }
        ),
        contentDescription = null,
        modifier = Modifier.size(200.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Title(stringResource(R.string.title_data_usage_page))

    Spacer(modifier = Modifier.height(Spacing.large))

    Description(stringResource(R.string.description_data_usage_page))

    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showPrivacyPolicyDialog = true },
        modifier = Modifier.testTag("privacy_button")
    ) {
        Text(
            text = stringResource(R.string.privacy_policy),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }

    Spacer(modifier = Modifier.height(Spacing.extraLarge))

    Card(
        shape = CardStyle.shape,
        colors = CardStyle.colors,
        onClick = { onAcceptedChange(!viewModel.termsAccepted) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agreement_card"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.medium),
        ) {
            Checkbox(
                checked = viewModel.termsAccepted,
                onCheckedChange = onAcceptedChange,
                modifier = Modifier.testTag("agreement_checkbox")
            )
            Text(
                text = stringResource(R.string.agreement_policy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RollingEntry(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationProgress = remember { Animatable(0f) }

    val density = LocalDensity.current
    val startOffsetPx = with(density) { -200.dp.toPx() }

    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val translationX = startOffsetPx * (1 - animationProgress.value)
    val rotation = -360f * (1 - animationProgress.value)

    Box(
        modifier = modifier.graphicsLayer {
            this.translationX = translationX
            this.rotationZ = rotation
            this.alpha = animationProgress.value.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}


@Composable
fun AnimateFloating(
    amplitudeDp: Float = 6f,
    content: @Composable () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "float")
    val offsetY by infinite.animateFloat(
        initialValue = -amplitudeDp,
        targetValue = amplitudeDp,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        modifier = Modifier.offset(y = offsetY.dp)
    ) {
        content()
    }
}

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
            delay(100)
            blinking = false
            delay(150)
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
fun ShuffleSmileys(
    verygood: Int,
    good: Int,
    sad: Int,
    verysad: Int
) {
    var smile by remember { mutableIntStateOf(verygood) }

    LaunchedEffect(Unit) {

        while (true) {
            delay(1000)
            smile = verygood
            delay(1000)
            smile = good
            delay(1000)
            smile = sad
            delay(1000)
            smile = verysad
        }
    }

    Icon(
        painter = painterResource(smile),
        contentDescription = null,
        modifier = Modifier.size(140.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Title(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onBackground,
)


@Composable
private fun Description(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Spacing.large)
)

open class WelcomeViewModel(
    private val dao: UserProfileDao
) : ViewModel() {
    var termsAccepted by mutableStateOf(false)

    val checkedIn: StateFlow<Boolean> = dao.getCheckedInLive()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun completeOnboarding() {
        viewModelScope.launch {
            dao.updateCheckedIn(true)
        }
    }
}