package com.google.android.system

/**
 * NormalizedPoint
 *
 * Intermediate 0.0 .. 1.0 normalized position.
 */
data class NormalizedPoint(
    val u: Double,
    val v: Double
)

/**
 * AndroidInputPoint
 *
 * Verified native Android input touch coordinate.
 */
data class AndroidInputPoint(
    val x: Float,
    val y: Float,
    val geometryVersion: Long
)

/**
 * TransformationResult
 */
sealed class TransformationResult {
    data class Success(val point: AndroidInputPoint) : TransformationResult()
    data class Error(val reason: String) : TransformationResult()
}

/**
 * WebStreamCoordinateTransformer
 *
 * Converts normalized (u, v) points into verified Android input coordinates
 * using thread-safe display geometry snapshots and edge-safe clamping.
 */
object WebStreamCoordinateTransformer {

    fun transform(point: NormalizedPoint, geometry: WebStreamDisplayGeometry): TransformationResult {
        if (!geometry.isValid) {
            return TransformationResult.Error("Display geometry is invalid")
        }

        if (geometry.inputWidth <= 0 || geometry.inputHeight <= 0) {
            return TransformationResult.Error("Input dimensions must be greater than zero")
        }

        val u = point.u
        val v = point.v

        if (u.isNaN() || v.isNaN() || u.isInfinite() || v.isInfinite()) {
            return TransformationResult.Error("Normalized coordinates contain NaN or Infinity")
        }

        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
            return TransformationResult.Error("Normalized coordinates out of bounds [0.0, 1.0]: u=$u, v=$v")
        }

        val maxX = (geometry.inputWidth - 1f).coerceAtLeast(0f)
        val maxY = (geometry.inputHeight - 1f).coerceAtLeast(0f)

        val rawX = (u * geometry.inputWidth).toFloat()
        val rawY = (v * geometry.inputHeight).toFloat()

        val clampedX = rawX.coerceIn(0f, maxX)
        val clampedY = rawY.coerceIn(0f, maxY)

        return TransformationResult.Success(
            AndroidInputPoint(
                x = clampedX,
                y = clampedY,
                geometryVersion = geometry.geometryVersion
            )
        )
    }
}
