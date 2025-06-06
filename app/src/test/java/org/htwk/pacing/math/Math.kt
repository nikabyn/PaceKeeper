package org.htwk.pacing.math

import org.htwk.pacing.assertFloat
import org.junit.Test

class Math {
    @Test
    fun interpolate_isValid() {
        val epsilon = 1e-5f
        assertFloat(interpolate(0f, 1f, 0.5f), 0.5f, epsilon)
        assertFloat(interpolate(1f, 0f, 0.5f), 0.5f, epsilon)
        assertFloat(interpolate(0f, 50f, 0.2f), 10f, epsilon)
        assertFloat(interpolate(50f, 0f, 0.2f), 40f, epsilon)
        assertFloat(interpolate(10f, 100f, 0.4f), 46f, epsilon)
        assertFloat(interpolate(100f, 10f, 0.6f), 46f, epsilon)
    }

    @Test
    fun remap_isValid() {
        val epsilon = 1e-5f
        assertFloat(remap(0f, 0f, 1f, 10f, 100f), 10f, epsilon)
    }
}