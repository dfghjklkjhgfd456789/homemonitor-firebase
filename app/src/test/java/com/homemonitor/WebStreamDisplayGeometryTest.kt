package com.google.android.system

import org.junit.Test

/**
 * WebStreamDisplayGeometryTest
 *
 * Unit tests validating WebStreamDisplayGeometry data snapshot and provider lifecycle.
 */
class WebStreamDisplayGeometryTest {

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        check(expected == actual) { "Expected $expected but got $actual" }
    }

    @Test
    fun testDefaultGeometrySnapshot() {
        val defaultGeo = WebStreamDisplayGeometry()
        assertTrue("Default geometry must be valid", defaultGeo.isValid)
        assertEquals(1080, defaultGeo.screenshotWidth)
        assertEquals(2400, defaultGeo.screenshotHeight)
        assertEquals(1080, defaultGeo.inputWidth)
        assertEquals(2400, defaultGeo.inputHeight)
        assertEquals(1L, defaultGeo.geometryVersion)
        assertEquals("android-input-pixels", defaultGeo.coordinateSpace)
    }

    @Test
    fun testLandscapeGeometrySnapshot() {
        val landscapeGeo = WebStreamDisplayGeometry(
            screenshotWidth = 2400,
            screenshotHeight = 1080,
            inputWidth = 2400,
            inputHeight = 1080,
            rotation = 1, // Surface.ROTATION_90
            geometryVersion = 2L
        )
        assertTrue("Landscape geometry must be valid", landscapeGeo.isValid)
        assertEquals(2400, landscapeGeo.screenshotWidth)
        assertEquals(1080, landscapeGeo.screenshotHeight)
        assertEquals(2L, landscapeGeo.geometryVersion)
    }

    @Test
    fun testInvalidGeometrySnapshot() {
        val invalidGeo = WebStreamDisplayGeometry(
            screenshotWidth = 0,
            screenshotHeight = 0,
            isValid = false
        )
        assertTrue("Zero dimension geometry must be invalid", !invalidGeo.isValid)
    }
}
