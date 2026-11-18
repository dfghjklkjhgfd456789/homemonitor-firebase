package com.google.android.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data Transfer Objects for E5 Precision Gestures
 */
data class PointDto(val u: Double, val v: Double)

data class StrokeDto(
    val pointerId: Int = 1,
    val startOffsetMs: Long = 0L,
    val durationMs: Long = 300L,
    val points: List<PointDto> = emptyList()
)

data class GestureRequestDto(
    val schemaVersion: Int = 1,
    val requestId: String = "",
    val geometryVersion: Long = 1L,
    val strokes: List<StrokeDto> = emptyList()
) {
    companion object {
        fun fromJson(jsonStr: String): GestureRequestDto {
            val json = JSONObject(jsonStr)
            val schemaVersion = json.optInt("schemaVersion", 1)
            val requestId = json.optString("requestId", "")
            val geometryVersion = json.optLong("geometryVersion", 1L)

            val strokesList = mutableListOf<StrokeDto>()
            val strokesArray = json.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until strokesArray.length()) {
                val strokeObj = strokesArray.optJSONObject(i) ?: continue
                val pointerId = strokeObj.optInt("pointerId", i + 1)
                val startOffsetMs = strokeObj.optLong("startOffsetMs", 0L)
                val durationMs = strokeObj.optLong("durationMs", 300L)

                val pointsList = mutableListOf<PointDto>()
                val pointsArray = strokeObj.optJSONArray("points") ?: JSONArray()
                for (j in 0 until pointsArray.length()) {
                    val pObj = pointsArray.optJSONObject(j) ?: continue
                    pointsList.add(
                        PointDto(
                            u = pObj.optDouble("u", 0.0),
                            v = pObj.optDouble("v", 0.0)
                        )
                    )
                }
                strokesList.add(
                    StrokeDto(
                        pointerId = pointerId,
                        startOffsetMs = startOffsetMs,
                        durationMs = durationMs,
                        points = pointsList
                    )
                )
            }

            return GestureRequestDto(
                schemaVersion = schemaVersion,
                requestId = requestId,
                geometryVersion = geometryVersion,
                strokes = strokesList
            )
        }
    }
}

sealed class DispatchResult {
    data class Success(val requestId: String, val strokeCount: Int) : DispatchResult()
    data class Error(val reason: String) : DispatchResult()
}

/**
 * WebStreamGestureController
 *
 * Native Android gesture controller building and dispatching multi-stroke GestureDescription
 * objects via AccessibilityService with request deduplication and lifecycle serialization.
 */
object WebStreamGestureController {

    private const val TAG = "WebStreamGestureCtrl"

    // Application limits
    const val MAX_SIMULTANEOUS_POINTERS = 2
    const val MAX_PATH_POINTS_PER_STROKE = 64
    const val MAX_GESTURE_DURATION_MS = 5000L
    private const val DEDUPLICATION_CACHE_MAX_SIZE = 200

    private val isDispatching = AtomicBoolean(false)
    private val processedRequestIds = ConcurrentHashMap.newKeySet<String>()

    fun validateRequest(request: GestureRequestDto, currentGeometry: WebStreamDisplayGeometry): DispatchResult? {
        if (!currentGeometry.isValid) {
            return DispatchResult.Error("Android display geometry is invalid")
        }

        if (request.geometryVersion != currentGeometry.geometryVersion) {
            return DispatchResult.Error("Stale geometry version: request=${request.geometryVersion}, active=${currentGeometry.geometryVersion}")
        }

        if (request.requestId.isNotEmpty() && processedRequestIds.contains(request.requestId)) {
            return DispatchResult.Error("Duplicate request ID: ${request.requestId}")
        }

        if (request.strokes.isEmpty()) {
            return DispatchResult.Error("Gesture request contains no strokes")
        }

        if (request.strokes.size > MAX_SIMULTANEOUS_POINTERS) {
            return DispatchResult.Error("Strokes count (${request.strokes.size}) exceeds maximum allowed pointers ($MAX_SIMULTANEOUS_POINTERS)")
        }

        for ((idx, stroke) in request.strokes.withIndex()) {
            if (stroke.durationMs < 10L || stroke.durationMs > MAX_GESTURE_DURATION_MS) {
                return DispatchResult.Error("Stroke #$idx duration (${stroke.durationMs}ms) out of bounds [10ms, ${MAX_GESTURE_DURATION_MS}ms]")
            }

            if (stroke.points.isEmpty()) {
                return DispatchResult.Error("Stroke #$idx has no points")
            }

            if (stroke.points.size > MAX_PATH_POINTS_PER_STROKE) {
                return DispatchResult.Error("Stroke #$idx point count (${stroke.points.size}) exceeds maximum allowed ($MAX_PATH_POINTS_PER_STROKE)")
            }

            for (p in stroke.points) {
                if (p.u.isNaN() || p.v.isNaN() || p.u.isInfinite() || p.v.isInfinite()) {
                    return DispatchResult.Error("Stroke #$idx contains NaN or Infinity coordinate")
                }
                if (p.u < 0.0 || p.u > 1.0 || p.v < 0.0 || p.v > 1.0) {
                    return DispatchResult.Error("Stroke #$idx coordinate out of bounds [0.0, 1.0]: u=${p.u}, v=${p.v}")
                }
            }
        }

        return null // Valid
    }

    fun dispatchGestureRequest(request: GestureRequestDto, context: Context): DispatchResult {
        val accessService = AppAccessibilityService.instance
            ?: return DispatchResult.Error("AccessibilityService instance is not active")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return DispatchResult.Error("GestureDescription requires Android 7.0+ (API 24+)")
        }

        val currentGeometry = WebStreamDisplayGeometryProvider.getGeometry(context)
        val validationError = validateRequest(request, currentGeometry)
        if (validationError != null) {
            return validationError
        }

        if (!isDispatching.compareAndSet(false, true)) {
            return DispatchResult.Error("Gesture dispatcher is busy executing another gesture")
        }

        return try {
            val builder = GestureDescription.Builder()

            for (stroke in request.strokes) {
                val path = Path()
                for ((i, p) in stroke.points.withIndex()) {
                    val normPoint = NormalizedPoint(p.u, p.v)
                    val transformRes = WebStreamCoordinateTransformer.transform(normPoint, currentGeometry)
                    if (transformRes is TransformationResult.Success) {
                        val x = transformRes.point.x
                        val y = transformRes.point.y
                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    } else {
                        isDispatching.set(false)
                        return DispatchResult.Error("Coordinate transformation failed for point (${p.u}, ${p.v})")
                    }
                }

                val strokeDesc = GestureDescription.StrokeDescription(
                    path,
                    stroke.startOffsetMs.coerceAtLeast(0L),
                    stroke.durationMs.coerceAtLeast(10L)
                )
                builder.addStroke(strokeDesc)
            }

            val gesture = builder.build()
            val mainHandler = Handler(Looper.getMainLooper())

            val dispatched = accessService.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.i(TAG, "Gesture [${request.requestId}] completed successfully")
                        if (request.requestId.isNotEmpty()) {
                            recordRequestId(request.requestId)
                        }
                        isDispatching.set(false)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Gesture [${request.requestId}] cancelled")
                        isDispatching.set(false)
                    }
                },
                mainHandler
            )

            if (dispatched) {
                DispatchResult.Success(request.requestId, request.strokes.size)
            } else {
                isDispatching.set(false)
                DispatchResult.Error("AccessibilityService failed to dispatch gesture")
            }
        } catch (e: Exception) {
            isDispatching.set(false)
            Log.e(TAG, "dispatchGesture error: ${e.message}", e)
            DispatchResult.Error("Gesture dispatch error: ${e.message}")
        }
    }

    private fun recordRequestId(reqId: String) {
        if (processedRequestIds.size >= DEDUPLICATION_CACHE_MAX_SIZE) {
            val iterator = processedRequestIds.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        processedRequestIds.add(reqId)
    }

    fun clearState() {
        isDispatching.set(false)
        processedRequestIds.clear()
    }
}
