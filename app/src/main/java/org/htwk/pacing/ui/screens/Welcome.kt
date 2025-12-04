package org.htwk.pacing.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.ui.logo.BlinkLogo
import org.htwk.pacing.ui.logo.Floaty
import org.htwk.pacing.ui.logo.RollingEntry
import org.htwk.pacing.ui.logo.shuffleSmileys
import org.htwk.pacing.ui.theme.Spacing

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: Int,
    val optionalIconClosed: Int? = null
)

val pages = listOf(
    OnboardingPage(
        "Willkommen",
        "PaceKeeper hilft dir, dein Energie-Budget im Auge zu behalten.",
        R.drawable.ic_logo_open,
        R.drawable.ic_logo_closed
    ),
    OnboardingPage(
        "Pacing verstehen",
        "Vermeide den Crash. Bleibe in deinem sicheren Bereich.",
        R.drawable.rounded_show_chart_24
    ),
    OnboardingPage(
        "Dich selbst entdecken",
        "Beobachte deine Symptome. Erkenne deine Muster.",
        R.drawable.very_happy,
        R.drawable.very_sad
    ),
    OnboardingPage(
        "Loslegen",
        "Höre auf deinen Körper. Wir starten jetzt.",
        R.drawable.ic_logo_open,
        R.drawable.ic_logo_closed
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var termsAccepted by remember { mutableStateOf(false) }
    val isLastPage = pagerState.currentPage == pages.size - 1

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                /*
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
                */
                if (isSystemInDarkTheme())
                    Color(0xff320014)
                else
                    Color(0xFFFFF0F6)
            )
            .padding(Spacing.medium)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(
                    page = pages[pageIndex],
                    pageIndex = pageIndex,
                    showCheckbox = pageIndex == pages.size - 1,
                    isChecked = termsAccepted,
                    onCheckedChange = { termsAccepted = it }
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    repeat(pages.size) { iteration ->
                        val color =
                            if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(10.dp)
                        )
                    }
                }

                val buttonEnabled = if (isLastPage) termsAccepted else true
                Button(
                    onClick = {
                        if (isLastPage) onFinished() else scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1
                            )
                        }
                    },
                    enabled = buttonEnabled,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    AnimatedVisibility(visible = isLastPage) { Icon(Icons.Default.Check, "Fertig") }
                    AnimatedVisibility(visible = !isLastPage) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            "Weiter"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    pageIndex: Int,
    showCheckbox: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (pageIndex) {
            0 -> {
                RollingEntry {
                    Floaty {
                        if (page.optionalIconClosed != null) {
                            BlinkLogo(
                                open = page.icon,
                                closed = page.optionalIconClosed
                            )
                        } else {
                            Image(
                                painter = painterResource(page.icon),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
            }

            1 -> {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = page.icon),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            2 -> {
                RollingEntry {
                    Floaty {
                        if (page.optionalIconClosed != null) {
                            shuffleSmileys(
                                open = page.icon,
                                closed = page.optionalIconClosed
                            )
                        } else {
                            Image(
                                painter = painterResource(page.icon),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
            }

            3 -> {
                if (isChecked && page.optionalIconClosed != null) {
                    Image(
                        painter = painterResource(id = page.optionalIconClosed),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(page.icon),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.large)
        )

        if (showCheckbox) {
            Spacer(modifier = Modifier.height(Spacing.extraLarge))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(Spacing.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(Spacing.small)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(Spacing.extraLarge))
                Text(
                    text = "Ich habe verstanden und möchte starten.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.small),
                    color = if (isSystemInDarkTheme()) Color.White else Color.Unspecified
                )
            }
        }
    }
}

class WelcomeViewModel(
    private val db: PacingDatabase
) : ViewModel() {

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            //TODO save in db
            onSuccess()
        }
    }
}