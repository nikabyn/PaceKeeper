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
        assertFloat(interpolate(0f, 1f, 10f), 10f, epsilon)
        assertFloat(interpolate(1f, 0f, 10f), -9f, epsilon)
        assertFloat(interpolate(1f, 1f, 0.5f), 1f, epsilon)
        assertFloat(interpolate(100f, 100f, 1f), 100f, epsilon)
        assertFloat(interpolate(0f, 1f, 0f), 0f, epsilon)
    }

    @Test
    fun remap_isValid() {
        val epsilon = 1e-5f
        assertFloat(remap(0f, 0f, 1f, 10f, 100f), 10f, epsilon)
        assertFloat(remap(0f, 0f, 1f, 0f, 0f), 0f, epsilon)
        assertFloat(remap(0f, 0f, 0f, 10f, 100f), 0f, epsilon)
        assertFloat(remap(0f, 0f, 1f, 100f, 10f), 100f, epsilon)
        assertFloat(remap(0f, 0f, 1f, 1f, 10000f), 1f, epsilon)
        assertFloat(remap(8f, 0f, 1f, 0f, 100f), 800f, epsilon)
        assertFloat(remap(-9f, 0f, 1f, 0f, -100f), 900f, epsilon)
        assertFloat(remap(12f, 0f, 20f, 0f, 10f), 6f, epsilon)
        assertFloat(remap(90f, 0f, 10f, 1f, 2f), 10f, epsilon)
        assertFloat(remap(0.5f, 0f, 1f, 0f, 3f), 1.5f, epsilon)
        assertFloat(remap(1f, 0f, 4f, 0f, 1f), 0.25f, epsilon)
        assertFloat(remap(8f, 0f, 80f, 80f, 0f), 72f, epsilon)
    }
}