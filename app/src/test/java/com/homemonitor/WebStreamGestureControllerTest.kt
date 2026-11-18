package com.google.android.system

import org.junit.Test

/**
 * WebStreamGestureControllerTest
 *
 * Unit tests validating WebStreamGestureController state cleanup and json serialization.
 */
class WebStreamGestureControllerTest {

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        check(expected == actual) { "Expected $expected but got $actual" }
    }

    @Test
    fun testJsonDeserialization() {
        val jsonStr = """
            {
              "schemaVersion": 1,
              "requestId": "req_555",
              "geometryVersion": 4,
              "strokes": [
                {
                  "pointerId": 1,
                  "startOffsetMs": 0,
                  "durationMs": 300,
                  "points": [
                    {"u": 0.1, "v": 0.2},
                    {"u": 0.3, "v": 0.4}
                  ]
                }
              ]
            }
        """.trimIndent()

        val dto = GestureRequestDto.fromJson(jsonStr)
        assertEquals(1, dto.schemaVersion)
        assertEquals("req_555", dto.requestId)
        assertEquals(4L, dto.geometryVersion)
        assertEquals(1, dto.strokes.size)
        assertEquals(2, dto.strokes[0].points.size)
        assertEquals(0.1, dto.strokes[0].points[0].u)
    }

    @Test
    fun testClearState() {
        WebStreamGestureController.clearState()
        val geometry = WebStreamDisplayGeometry(geometryVersion = 1L)
        val req = GestureRequestDto(requestId = "req_unique_1", geometryVersion = 1L, strokes = listOf(StrokeDto(points = listOf(PointDto(0.5, 0.5)))))
        
        val err1 = WebStreamGestureController.validateRequest(req, geometry)
        check(err1 == null)
    }
}
