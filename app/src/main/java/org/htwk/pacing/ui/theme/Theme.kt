package org.htwk.pacing.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object PacingTheme {
    val colors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}

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

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) ExtendedColors(
        red = ColorPalette.red80,
        orange = ColorPalette.orange80,
        yellow = ColorPalette.yellow80,
        green = ColorPalette.green80,
        cyan = ColorPalette.green80,
        blue = ColorPalette.green80,
    ) else ExtendedColors(
        red = ColorPalette.red60,
        orange = ColorPalette.orange60,
        yellow = ColorPalette.yellow60,
        green = ColorPalette.green60,
        cyan = ColorPalette.green60,
        blue = ColorPalette.green60,
    )

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Immutable
data class ExtendedColors(
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val cyan: Color,
    val blue: Color,
)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        red = Color.Unspecified,
        orange = Color.Unspecified,
        yellow = Color.Unspecified,
        green = Color.Unspecified,
        cyan = Color.Unspecified,
        blue = Color.Unspecified,
    )
}