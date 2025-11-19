package org.htwk.pacing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.htwk.pacing.R

/**
 * Variable fonts are supported by all of our
 */
@OptIn(ExperimentalTextApi::class)
private val fontFamily = FontFamily(Font(resId = R.font.google_sans_flex))

val Typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = fontFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = fontFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = fontFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = fontFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = Typography().bodySmall.copy(fontFamily = fontFamily),
    labelLarge = Typography().labelLarge.copy(fontFamily = fontFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = fontFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = fontFamily),
)