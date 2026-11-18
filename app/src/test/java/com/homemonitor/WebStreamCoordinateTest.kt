package com.google.android.system

import org.junit.Test

/**
 * WebStreamCoordinateTest
 *
 * Unit tests validating edge-safe coordinate transformation logic:
 * 1. Top-Left corner mapping
 * 2. Top-Right corner mapping
 * 3. Bottom-Left corner mapping
 * 4. Bottom-Right corner mapping
 * 5. Center point mapping
 * 6. Edge clamping (never exceeding screen bounds)
 * 7. High-precision floating point preservation
 * 8. Resolution changes & orientation switching (Portrait vs Landscape)
 */
class WebStreamCoordinateTest {

    private fun transformCoordinate(rel: Float, maxDimension: Float): Float {
        val maxBound = (maxDimension - 1f).coerceAtLeast(0f)
        return (rel * maxDimension).coerceIn(0f, maxBound)
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float = 0.001f) {
        check(kotlin.math.abs(expected - actual) <= delta) {
            "Expected $expected within $delta but got $actual"
        }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    @Test
    fun testTopLeftCorner() {
        val width = 1080f
        val height = 2400f
        val realX = transformCoordinate(0.0f, width)
        val realY = transformCoordinate(0.0f, height)
        assertEquals(0.0f, realX)
        assertEquals(0.0f, realY)
    }

    @Test
    fun testTopRightCorner() {
        val width = 1080f
        val height = 2400f
        val realX = transformCoordinate(1.0f, width)
        val realY = transformCoordinate(0.0f, height)
        assertEquals(1079.0f, realX)
        assertEquals(0.0f, realY)
    }

    @Test
    fun testBottomLeftCorner() {
        val width = 1080f
        val height = 2400f
        val realX = transformCoordinate(0.0f, width)
        val realY = transformCoordinate(1.0f, height)
        assertEquals(0.0f, realX)
        assertEquals(2399.0f, realY)
    }

    @Test
    fun testBottomRightCorner() {
        val width = 1080f
        val height = 2400f
        val realX = transformCoordinate(1.0f, width)
        val realY = transformCoordinate(1.0f, height)
        assertEquals(1079.0f, realX)
        assertEquals(2399.0f, realY)
    }

    @Test
    fun testCenterPoint() {
        val width = 1080f
        val height = 2400f
        val realX = transformCoordinate(0.5f, width)
        val realY = transformCoordinate(0.5f, height)
        assertEquals(540.0f, realX)
        assertEquals(1200.0f, realY)
    }

    @Test
    fun testFractionalPrecision() {
        val width = 1080f
        val height = 2400f
        val relX = 0.33333333f
        val relY = 0.66666667f
        val realX = transformCoordinate(relX, width)
        val realY = transformCoordinate(relY, height)
        assertEquals(359.99996f, realX, 0.01f)
        assertEquals(1600.0f, realY, 0.01f)
    }

    @Test
    fun testOutofBoundsClamping() {
        val width = 1080f
        val height = 2400f
        val realXLow = transformCoordinate(-0.05f, width)
        val realYHigh = transformCoordinate(1.05f, height)
        assertEquals(0.0f, realXLow)
        assertEquals(2399.0f, realYHigh)
        assertTrue("X must not be negative", realXLow >= 0.0f)
        assertTrue("Y must not exceed screen height - 1", realYHigh <= 2399.0f)
    }

    @Test
    fun testLandscapeResolutionSwitch() {
        val width = 2400f  // Landscape width
        val height = 1080f // Landscape height

        val realXRight = transformCoordinate(1.0f, width)
        val realYBottom = transformCoordinate(1.0f, height)
        val realXCenter = transformCoordinate(0.5f, width)
        val realYCenter = transformCoordinate(0.5f, height)

        assertEquals(2399.0f, realXRight)
        assertEquals(1079.0f, realYBottom)
        assertEquals(1200.0f, realXCenter)
        assertEquals(540.0f, realYCenter)
    }
}
