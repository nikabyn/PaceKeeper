package org.htwk.pacing.math

import org.htwk.pacing.assertDouble
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.math.remap
import org.junit.Test

class Math {
    @Test
    fun interpolate_isValid() {
        val epsilon = 1e-5
        assertDouble(interpolate(0.0, 1.0, 0.5f), 0.5, epsilon)
        assertDouble(interpolate(1.0, 0.0, 0.5f), 0.5, epsilon)
        assertDouble(interpolate(0.0, 50.0, 0.2f), 10.0, epsilon)
        assertDouble(interpolate(50.0, 0.0, 0.2f), 40.0, epsilon)
        assertDouble(interpolate(10.0, 100.0, 0.4f), 46.0, epsilon)
        assertDouble(interpolate(100.0, 10.0, 0.6f), 46.0, epsilon)
        assertDouble(interpolate(0.0, 1.0, 10f), 10.0, epsilon)
        assertDouble(interpolate(1.0, 0.0, 10f), -9.0, epsilon)
        assertDouble(interpolate(1.0, 1.0, 0.5f), 1.0, epsilon)
        assertDouble(interpolate(100.0, 100.0, 1f), 100.0, epsilon)
        assertDouble(interpolate(0.0, 1.0, 0f), 0.0, epsilon)
    }

    @Test
    fun remap_isValid() {
        val epsilon = 1e-5
        assertDouble(remap(0.0, 0.0, 1.0, 10.0, 100.0), 10.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 0.0, 0.0), 0.0, epsilon)
        assertDouble(remap(0.0, 0.0, 0.0, 10.0, 100.0), 0.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 100.0, 10.0), 100.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 1.0, 10000.0), 1.0, epsilon)
        assertDouble(remap(8.0, 0.0, 1.0, 0.0, 100.0), 800.0, epsilon)
        assertDouble(remap(-9.0, 0.0, 1.0, 0.0, -100.0), 900.0, epsilon)
        assertDouble(remap(12.0, 0.0, 20.0, 0.0, 10.0), 6.0, epsilon)
        assertDouble(remap(90.0, 0.0, 10.0, 1.0, 2.0), 10.0, epsilon)
        assertDouble(remap(0.5, 0.0, 1.0, 0.0, 3.0), 1.5, epsilon)
        assertDouble(remap(1.0, 0.0, 4.0, 0.0, 1.0), 0.25, epsilon)
        assertDouble(remap(8.0, 0.0, 80.0, 80.0, 0.0), 72.0, epsilon)
    }
}