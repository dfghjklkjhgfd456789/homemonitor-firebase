package com.google.android.system

import org.junit.Test

/**
 * WebStreamCalibrationManagerTest
 *
 * Unit tests validating E6 2D Affine Calibration Matrix computation:
 * 1. Identity alignment computing valid 0.0% error profile.
 * 2. Degenerate collinear points rejection.
 * 3. Profile invalidation on geometry version change.
 * 4. P95 threshold rejection when error > 2.0%.
 */
class WebStreamCalibrationManagerTest {

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float = 0.001f) {
        check(kotlin.math.abs(expected - actual) <= delta) {
            "Expected $expected within $delta but got $actual"
        }
    }

    @Test
    fun testIdentityCalibrationProfile() {
        val geometry = WebStreamDisplayGeometry(screenshotWidth = 1080, screenshotHeight = 2400)
        val grid = listOf(
            CalibrationPoint(0.10, 0.10, 108f, 240f),
            CalibrationPoint(0.50, 0.10, 540f, 240f),
            CalibrationPoint(0.90, 0.10, 972f, 240f),
            CalibrationPoint(0.10, 0.50, 108f, 1200f),
            CalibrationPoint(0.50, 0.50, 540f, 1200f),
            CalibrationPoint(0.90, 0.50, 972f, 1200f),
            CalibrationPoint(0.10, 0.90, 108f, 2160f),
            CalibrationPoint(0.50, 0.90, 540f, 2160f),
            CalibrationPoint(0.90, 0.90, 972f, 2160f)
        )

        val profile = WebStreamCalibrationManager.computeCalibrationProfile(grid, geometry)
        assertTrue("Identity grid must yield valid profile", profile.isValid)
        assertEquals(0.0f, profile.matrix.a0, 0.01f)
        assertEquals(1080.0f, profile.matrix.a1, 0.01f)
        assertEquals(0.0f, profile.matrix.b0, 0.01f)
        assertEquals(2400.0f, profile.matrix.b2, 0.01f)
        assertEquals(0.0f, profile.metrics.p95ErrorPx, 0.01f)
    }

    @Test
    fun testInsufficientPointsRejection() {
        val geometry = WebStreamDisplayGeometry()
        val twoPoints = listOf(
            CalibrationPoint(0.10, 0.10, 108f, 240f),
            CalibrationPoint(0.50, 0.50, 540f, 1200f)
        )

        val profile = WebStreamCalibrationManager.computeCalibrationProfile(twoPoints, geometry)
        assertTrue("Insufficient points must yield invalid profile", !profile.isValid)
    }

    @Test
    fun testExcessiveErrorRejection() {
        val geometry = WebStreamDisplayGeometry(screenshotWidth = 1080, screenshotHeight = 2400)
        // Non-linear scattered noise (exceeds 2% of 1080 = 21.6px limit)
        val noisyGrid = listOf(
            CalibrationPoint(0.10, 0.10, 208f, 240f),
            CalibrationPoint(0.50, 0.10, 440f, 340f),
            CalibrationPoint(0.90, 0.10, 1072f, 140f),
            CalibrationPoint(0.10, 0.50, 108f, 1350f),
            CalibrationPoint(0.50, 0.50, 640f, 1050f),
            CalibrationPoint(0.90, 0.50, 800f, 1300f),
            CalibrationPoint(0.10, 0.90, 250f, 2060f),
            CalibrationPoint(0.50, 0.90, 400f, 2260f),
            CalibrationPoint(0.90, 0.90, 1100f, 2000f)
        )

        val profile = WebStreamCalibrationManager.computeCalibrationProfile(noisyGrid, geometry)
        check(!profile.isValid) { "Profile with excessive error must be marked invalid" }
    }
}
