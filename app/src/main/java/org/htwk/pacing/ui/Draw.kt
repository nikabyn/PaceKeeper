/**
 * Extension functions for drawing.
 */

package org.htwk.pacing.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.htwk.pacing.ui.math.Float2D

/**
 * Transforms 0f..=1f to 0f..=size.width or 0f..=size.height.
 */
fun Float2D.toPx(size: Size): Offset =
    Offset(x * size.width, y * size.height)

/**
 * Same as the default moveTo function, but transforms value to pixels beforehand.
 */
fun Path.moveTo(scope: DrawScope, value: Float2D) {
    val pos = value.toPx(scope.size)
    moveTo(pos.x, pos.y)
}

/**
 * Same as the default lineTo function, but transforms value to pixels beforehand.
 */
fun Path.lineTo(scope: DrawScope, value: Float2D) {
    val pos = value.toPx(scope.size)
    lineTo(pos.x, pos.y)
}

/**
 * Same as the default relativeLineTo function, but transforms value to pixels beforehand.
 */
fun Path.relativeLineTo(scope: DrawScope, value: Float2D) {
    relativeLineTo(value.x * scope.size.width, value.y * scope.size.height)
}
