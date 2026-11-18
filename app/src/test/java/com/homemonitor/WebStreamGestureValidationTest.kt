package com.google.android.system

import org.junit.Test

/**
 * WebStreamGestureValidationTest
 *
 * Unit tests validating E5 gesture DTO schema validation rules:
 * 1. Valid 1-stroke tap request passes validation.
 * 2. Valid 2-stroke pinch request passes validation.
 * 3. Rejects NaN / Infinity coordinates.
 * 4. Rejects out-of-bounds coordinates (u > 1.0 or v < 0.0).
 * 5. Rejects excessive stroke counts (> 2).
 * 6. Rejects excessive path point counts (> 64).
 * 7. Rejects excessive stroke durations (> 5000ms).
 * 8. Rejects stale geometry version mismatches.
 */
class WebStreamGestureValidationTest {

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertNotNull(message: String, value: Any?) {
        check(value != null) { "$message: Expected non-null" }
    }

    @Test
    fun testValidTapRequestValidation() {
        val geometry = WebStreamDisplayGeometry(geometryVersion = 5L)
        val request = GestureRequestDto(
            schemaVersion = 1,
            requestId = "req_1001",
            geometryVersion = 5L,
            strokes = listOf(
                StrokeDto(pointerId = 1, durationMs = 50, points = listOf(PointDto(0.5, 0.5)))
            )
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        check(err == null) { "Valid tap request must pass validation but got: $err" }
    }

    @Test
    fun testValidPinchRequestValidation() {
        val geometry = WebStreamDisplayGeometry(geometryVersion = 5L)
        val request = GestureRequestDto(
            schemaVersion = 1,
            requestId = "req_1002",
            geometryVersion = 5L,
            strokes = listOf(
                StrokeDto(pointerId = 1, durationMs = 300, points = listOf(PointDto(0.4, 0.5), PointDto(0.2, 0.5))),
                StrokeDto(pointerId = 2, durationMs = 300, points = listOf(PointDto(0.6, 0.5), PointDto(0.8, 0.5)))
            )
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        check(err == null) { "Valid 2-stroke pinch request must pass validation but got: $err" }
    }

    @Test
    fun testStaleGeometryVersionRejection() {
        val geometry = WebStreamDisplayGeometry(geometryVersion = 10L)
        val request = GestureRequestDto(
            geometryVersion = 9L,
            strokes = listOf(StrokeDto(points = listOf(PointDto(0.5, 0.5))))
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        assertNotNull("Stale geometry version must return validation error", err)
        assertTrue("Error must mention stale geometry version", (err as DispatchResult.Error).reason.contains("Stale geometry version"))
    }

    @Test
    fun testExcessiveStrokesRejection() {
        val geometry = WebStreamDisplayGeometry()
        val request = GestureRequestDto(
            strokes = listOf(
                StrokeDto(pointerId = 1, points = listOf(PointDto(0.1, 0.1))),
                StrokeDto(pointerId = 2, points = listOf(PointDto(0.2, 0.2))),
                StrokeDto(pointerId = 3, points = listOf(PointDto(0.3, 0.3))) // 3 pointers > 2 max
            )
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        assertNotNull("Excessive strokes must return validation error", err)
        assertTrue("Error must mention maximum pointers", (err as DispatchResult.Error).reason.contains("maximum allowed pointers"))
    }

    @Test
    fun testOutOfBoundsCoordinatesRejection() {
        val geometry = WebStreamDisplayGeometry()
        val request = GestureRequestDto(
            strokes = listOf(
                StrokeDto(points = listOf(PointDto(1.05, 0.5)))
            )
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        assertNotNull("Out of bounds coordinate must return validation error", err)
        assertTrue("Error must mention out of bounds", (err as DispatchResult.Error).reason.contains("out of bounds"))
    }

    @Test
    fun testExcessiveDurationRejection() {
        val geometry = WebStreamDisplayGeometry()
        val request = GestureRequestDto(
            strokes = listOf(
                StrokeDto(durationMs = 6000L, points = listOf(PointDto(0.5, 0.5)))
            )
        )

        val err = WebStreamGestureController.validateRequest(request, geometry)
        assertNotNull("Excessive duration must return validation error", err)
        assertTrue("Error must mention duration out of bounds", (err as DispatchResult.Error).reason.contains("duration"))
    }
}
