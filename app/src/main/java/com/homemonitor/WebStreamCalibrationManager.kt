package com.google.android.system

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class CalibrationPoint(
    val expectedU: Double,
    val expectedV: Double,
    val observedX: Float,
    val observedY: Float
)

data class CalibrationMetrics(
    val meanErrorPx: Float,
    val rmsErrorPx: Float,
    val maxErrorPx: Float,
    val p95ErrorPx: Float,
    val normalizedErrorPct: Float
)

data class AffineMatrix(
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val b0: Float,
    val b1: Float,
    val b2: Float
)

data class CalibrationProfile(
    val profileId: String,
    val geometryVersion: Long,
    val matrix: AffineMatrix,
    val metrics: CalibrationMetrics,
    val pointCount: Int,
    val isValid: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("profileId", profileId)
            put("geometryVersion", geometryVersion)
            put("isValid", isValid)
            put("pointCount", pointCount)
            put("matrix", JSONObject().apply {
                put("a0", matrix.a0.toDouble())
                put("a1", matrix.a1.toDouble())
                put("a2", matrix.a2.toDouble())
                put("b0", matrix.b0.toDouble())
                put("b1", matrix.b1.toDouble())
                put("b2", matrix.b2.toDouble())
            })
            put("metrics", JSONObject().apply {
                put("meanErrorPx", metrics.meanErrorPx.toDouble())
                put("rmsErrorPx", metrics.rmsErrorPx.toDouble())
                put("maxErrorPx", metrics.maxErrorPx.toDouble())
                put("p95ErrorPx", metrics.p95ErrorPx.toDouble())
                put("normalizedErrorPct", metrics.normalizedErrorPct.toDouble())
            })
        }
    }
}

/**
 * WebStreamCalibrationManager
 *
 * Computes 2D Affine Calibration matrices using least-squares fit across 9-point grid samples.
 * Rejects degenerate profiles or error > 2.0% of shorter display dimension.
 */
object WebStreamCalibrationManager {

    private const val TAG = "WebStreamCalibMgr"
    private const val PREFS_NAME = "webstream_calibration_store"

    @Volatile private var activeProfile: CalibrationProfile? = null

    fun getActiveProfile(geometryVersion: Long): CalibrationProfile? {
        val current = activeProfile ?: return null
        if (current.geometryVersion != geometryVersion || !current.isValid) {
            return null
        }
        return current
    }

    /**
     * Solves standard 2D Affine System using Least-Squares Linear Regression:
     * X' = a0 + a1 * u + a2 * v
     * Y' = b0 + b1 * u + b2 * v
     */
    fun computeCalibrationProfile(
        points: List<CalibrationPoint>,
        geometry: WebStreamDisplayGeometry
    ): CalibrationProfile {
        if (!geometry.isValid || points.size < 3) {
            return createInvalidProfile(geometry.geometryVersion, "Insufficient points (${points.size}) or invalid geometry")
        }

        val n = points.size.toDouble()
        var sumU = 0.0; var sumV = 0.0
        var sumU2 = 0.0; var sumV2 = 0.0; var sumUV = 0.0
        var sumX = 0.0; var sumXU = 0.0; var sumXV = 0.0
        var sumY = 0.0; var sumYU = 0.0; var sumYV = 0.0

        for (p in points) {
            val u = p.expectedU
            val v = p.expectedV
            val x = p.observedX.toDouble()
            val y = p.observedY.toDouble()

            sumU += u; sumV += v
            sumU2 += u * u; sumV2 += v * v; sumUV += u * v
            sumX += x; sumXU += x * u; sumXV += x * v
            sumY += y; sumYU += y * u; sumYV += y * v
        }

        // Solve 3x3 normal system for (a0, a1, a2) and (b0, b1, b2)
        val matrixA = arrayOf(
            doubleArrayOf(n, sumU, sumV),
            doubleArrayOf(sumU, sumU2, sumUV),
            doubleArrayOf(sumV, sumUV, sumV2)
        )

        val solX = solve3x3(matrixA, doubleArrayOf(sumX, sumXU, sumXV))
        val solY = solve3x3(matrixA, doubleArrayOf(sumY, sumYU, sumYV))

        if (solX == null || solY == null) {
            return createInvalidProfile(geometry.geometryVersion, "Degenerate collinear point matrix")
        }

        val affine = AffineMatrix(
            a0 = solX[0].toFloat(),
            a1 = solX[1].toFloat(),
            a2 = solX[2].toFloat(),
            b0 = solY[0].toFloat(),
            b1 = solY[1].toFloat(),
            b2 = solY[2].toFloat()
        )

        // Evaluate error metrics
        val errors = mutableListOf<Float>()
        var sumErrSquare = 0.0f
        var sumErr = 0.0f

        for (p in points) {
            val predX = affine.a0 + affine.a1 * p.expectedU.toFloat() + affine.a2 * p.expectedV.toFloat()
            val predY = affine.b0 + affine.b1 * p.expectedU.toFloat() + affine.b2 * p.expectedV.toFloat()

            val dx = predX - p.observedX
            val dy = predY - p.observedY
            val err = sqrt(dx * dx + dy * dy)

            errors.add(err)
            sumErr += err
            sumErrSquare += err * err
        }

        errors.sort()
        val meanErr = sumErr / points.size
        val rmsErr = sqrt(sumErrSquare / points.size)
        val maxErr = errors.last()
        val p95Idx = min((points.size * 0.95).toInt(), points.size - 1)
        val p95Err = errors[p95Idx]

        val minDimension = min(geometry.inputWidth, geometry.inputHeight).toFloat()
        val normPct = if (minDimension > 0f) (p95Err / minDimension) * 100f else 100f

        // Reject if P95 error > 2.0% of shorter display dimension
        val isValid = normPct <= 2.0f

        val metrics = CalibrationMetrics(
            meanErrorPx = meanErr,
            rmsErrorPx = rmsErr,
            maxErrorPx = maxErr,
            p95ErrorPx = p95Err,
            normalizedErrorPct = normPct
        )

        val profile = CalibrationProfile(
            profileId = "calib_${System.currentTimeMillis()}",
            geometryVersion = geometry.geometryVersion,
            matrix = affine,
            metrics = metrics,
            pointCount = points.size,
            isValid = isValid
        )

        if (isValid) {
            activeProfile = profile
            Log.i(TAG, "Calibration accepted: P95 error = ${p95Err}px (${normPct}%)")
        } else {
            Log.w(TAG, "Calibration rejected: P95 error (${normPct}%) exceeds 2.0% limit")
        }

        return profile
    }

    private fun createInvalidProfile(version: Long, reason: String): CalibrationProfile {
        Log.w(TAG, "Calibration failed: $reason")
        return CalibrationProfile(
            profileId = "invalid",
            geometryVersion = version,
            matrix = AffineMatrix(0f, 0f, 0f, 0f, 0f, 0f),
            metrics = CalibrationMetrics(999f, 999f, 999f, 999f, 100f),
            pointCount = 0,
            isValid = false
        )
    }

    private fun solve3x3(A: Array<DoubleArray>, B: DoubleArray): DoubleArray? {
        val det = A[0][0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1]) -
                  A[0][1] * (A[1][0] * A[2][2] - A[1][2] * A[2][0]) +
                  A[0][2] * (A[1][0] * A[2][1] - A[1][1] * A[2][0])

        if (abs(det) < 1e-9) return null

        val inv = Array(3) { DoubleArray(3) }
        inv[0][0] = (A[1][1] * A[2][2] - A[1][2] * A[2][1]) / det
        inv[0][1] = (A[0][2] * A[2][1] - A[0][1] * A[2][2]) / det
        inv[0][2] = (A[0][1] * A[1][2] - A[0][2] * A[1][1]) / det

        inv[1][0] = (A[1][2] * A[2][0] - A[1][0] * A[2][2]) / det
        inv[1][1] = (A[0][0] * A[2][2] - A[0][2] * A[2][0]) / det
        inv[1][2] = (A[0][2] * A[1][0] - A[0][0] * A[1][2]) / det

        inv[2][0] = (A[1][0] * A[2][1] - A[1][1] * A[2][0]) / det
        inv[2][1] = (A[0][1] * A[2][0] - A[0][0] * A[2][1]) / det
        inv[2][2] = (A[0][0] * A[1][1] - A[0][1] * A[1][0]) / det

        val X = DoubleArray(3)
        for (i in 0..2) {
            X[i] = inv[i][0] * B[0] + inv[i][1] * B[1] + inv[i][2] * B[2]
        }
        return X
    }

    fun resetCalibration() {
        activeProfile = null
    }
}
