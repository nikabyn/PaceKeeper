package org.htwk.pacing.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sets up the app theme with optional dark mode and dynamic color support.
 *
 * @param darkTheme Whether to use the dark theme. Defaults to system setting.
 * @param dynamicColor Whether to use dynamic colors (Android 12+). Defaults to true.
 * @param content The composable content to apply the theme to.
 */
@Composable
fun PacingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val extendedColors = currentExtendedColors(darkTheme)

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/** Provides access to the current [ExtendedColors] in the composition. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable
    get() = LocalExtendedColors.current

/** Standard spacing, gap, padding, etc. values used throughout the app for consistent layout. */
object Spacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
}

object CardStyle {
    val shape: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.large

    val colors: CardColors
        @Composable
        get() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
}

val PrimaryButtonStyle: ButtonStyle
    @Composable
    get() = ButtonStyle(
        colors = ButtonDefaults.buttonColors(),
    )

val SecondaryButtonStyle: ButtonStyle
    @Composable
    get() = ButtonStyle(
        colors = ButtonDefaults.textButtonColors(),
        border = { enabled ->
            BorderStroke(
                width = 2.dp,
                color =
                    if (enabled) ButtonDefaults.textButtonColors().contentColor
                    else ButtonDefaults.textButtonColors().disabledContentColor
            )
        }
    )

val TonalButtonStyle: ButtonStyle
    @Composable
    get() = ButtonStyle(
        colors = ButtonDefaults.filledTonalButtonColors(),
    )

data class ButtonStyle(
    val colors: ButtonColors,
    val shape: Shape = RoundedCornerShape(percent = 100),
    val border: (@Composable (enabled: Boolean) -> BorderStroke)? = null,
    val textStyle: @Composable () -> TextStyle = {
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    },
    val padding: PaddingValues = PaddingValues(defaultHorizontalPadding, defaultVerticalPadding),
    val minHeight: Dp = (defaultVerticalPadding * 2) + 20.dp
)

private val defaultHorizontalPadding = 16.dp
private val defaultVerticalPadding = 10.dp