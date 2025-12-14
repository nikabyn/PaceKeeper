package org.htwk.pacing.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.htwk.pacing.ui.theme.ButtonStyle

/**
 * Button that wraps [androidx.compose.material3.Button] with
 * a consistent style defined by [ButtonStyle].
 *
 * This button applies default minimum height, shape, colors, border, padding, and text style
 * according to the provided [style], while still allowing full composable content.
 *
 * See [androidx.compose.material3.Button] for documentation of the parameters.
 *
 * ## Example:
 * ```
 * Button(
 *     onClick = { /* handle click */ },
 *     style = ButtonStyle.Primary
 * ) {
 *     Text("Click me")
 * }
 * ```
 */
@Composable
fun Button(
    onClick: () -> Unit,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick,
        modifier = modifier.defaultMinSize(minHeight = style.minHeight),
        enabled,
        shape = style.shape,
        colors = style.colors,
        border = style.border?.let { it(enabled) },
        contentPadding = style.padding,
        interactionSource = interactionSource,
        content = {
            CompositionLocalProvider(LocalTextStyle provides style.textStyle()) {
                content()
            }
        },
    )
}