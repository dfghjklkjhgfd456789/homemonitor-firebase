package com.google.android.system

import org.junit.Test

/**
 * WebStreamCoordinateTransformerTest
 *
 * Unit tests validating E4 Kotlin coordinate transformer:
 * 1. Normalized points to Android input coordinates
 * 2. Bounds clamping [0, inputWidth - 1] and [0, inputHeight - 1]
 * 3. NaN / Infinity rejection
 * 4. Invalid geometry rejection
 * 5. Landscape vs Portrait resolution scaling
 */
class WebStreamCoordinateTransformerTest {

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float = 0.001f) {
        check(kotlin.math.abs(expected - actual) <= delta) {
            "Expected $expected within $delta but got $actual"
        }
    }

    @Test
    fun testCenterPointTransform() {
        val geometry = WebStreamDisplayGeometry(inputWidth = 1080, inputHeight = 2400)
        val point = NormalizedPoint(0.5, 0.5)

        val result = WebStreamCoordinateTransformer.transform(point, geometry)
        assertTrue("Result must be Success", result is TransformationResult.Success)

        val success = result as TransformationResult.Success
        assertEquals(540.0f, success.point.x)
        assertEquals(1200.0f, success.point.y)
        check(success.point.geometryVersion == geometry.geometryVersion)
    }

    @Test
    fun testTopLeftCornerTransform() {
        val geometry = WebStreamDisplayGeometry(inputWidth = 1080, inputHeight = 2400)
        val point = NormalizedPoint(0.0, 0.0)

        val result = WebStreamCoordinateTransformer.transform(point, geometry)
        assertTrue("Result must be Success", result is TransformationResult.Success)

        val success = result as TransformationResult.Success
        assertEquals(0.0f, success.point.x)
        assertEquals(0.0f, success.point.y)
    }

    @Test
    fun testBottomRightCornerTransform() {
        val geometry = WebStreamDisplayGeometry(inputWidth = 1080, inputHeight = 2400)
        val point = NormalizedPoint(1.0, 1.0)

        val result = WebStreamCoordinateTransformer.transform(point, geometry)
        assertTrue("Result must be Success", result is TransformationResult.Success)

        val success = result as TransformationResult.Success
        assertEquals(1079.0f, success.point.x)
        assertEquals(2399.0f, success.point.y)
    }

    @Test
    fun testLandscapeTransform() {
        val geometry = WebStreamDisplayGeometry(
            screenshotWidth = 2400,
            screenshotHeight = 1080,
            inputWidth = 2400,
            inputHeight = 1080,
            rotation = 1
        )
        val point = NormalizedPoint(1.0, 1.0)

        val result = WebStreamCoordinateTransformer.transform(point, geometry)
        assertTrue("Result must be Success", result is TransformationResult.Success)

        val success = result as TransformationResult.Success
        assertEquals(2399.0f, success.point.x)
        assertEquals(1079.0f, success.point.y)
    }

    @Test
    fun testNaNAndInfinityRejection() {
        val geometry = WebStreamDisplayGeometry()
        val nanPoint = NormalizedPoint(Double.NaN, 0.5)
        val infPoint = NormalizedPoint(0.5, Double.POSITIVE_INFINITY)

        val nanResult = WebStreamCoordinateTransformer.transform(nanPoint, geometry)
        assertTrue("NaN must be rejected", nanResult is TransformationResult.Error)

        val infResult = WebStreamCoordinateTransformer.transform(infPoint, geometry)
        assertTrue("Infinity must be rejected", infResult is TransformationResult.Error)
    }

    @Test
    fun testOutOfBoundsRejection() {
        val geometry = WebStreamDisplayGeometry()
        val lowPoint = NormalizedPoint(-0.01, 0.5)
        val highPoint = NormalizedPoint(0.5, 1.01)

        val lowResult = WebStreamCoordinateTransformer.transform(lowPoint, geometry)
        assertTrue("u < 0.0 must be rejected", lowResult is TransformationResult.Error)

        val highResult = WebStreamCoordinateTransformer.transform(highPoint, geometry)
        assertTrue("v > 1.0 must be rejected", highResult is TransformationResult.Error)
    }

    @Test
    fun testInvalidGeometryRejection() {
        val invalidGeometry = WebStreamDisplayGeometry(isValid = false)
        val point = NormalizedPoint(0.5, 0.5)

        val result = WebStreamCoordinateTransformer.transform(point, invalidGeometry)
        assertTrue("Invalid geometry must be rejected", result is TransformationResult.Error)
    }
}
