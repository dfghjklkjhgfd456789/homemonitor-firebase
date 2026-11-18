package com.google.android.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/**
 * WebStreamDisplayGeometry
 *
 * Immutable data snapshot representing current Android display resolution,
 * screenshot dimensions, input injection coordinate bounds, and rotation state.
 */
data class WebStreamDisplayGeometry(
    val displayId: Int = Display.DEFAULT_DISPLAY,
    val screenshotWidth: Int = 1080,
    val screenshotHeight: Int = 2400,
    val inputWidth: Int = 1080,
    val inputHeight: Int = 2400,
    val rotation: Int = Surface.ROTATION_0,
    val densityDpi: Int? = 420,
    val geometryVersion: Long = 1L,
    val coordinateSpace: String = "android-input-pixels",
    val isValid: Boolean = true
)

/**
 * WebStreamDisplayGeometryProvider
 *
 * Reusable thread-safe provider retrieving real-time display geometry
 * and managing geometry revision versioning.
 */
object WebStreamDisplayGeometryProvider {

    private val versionCounter = AtomicLong(1L)
    @Volatile private var cachedGeometry: WebStreamDisplayGeometry? = null

    fun getGeometry(context: Context): WebStreamDisplayGeometry {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val realMetrics = DisplayMetrics()
        if (defaultDisplay != null) {
            defaultDisplay.getRealMetrics(realMetrics)
        } else {
            realMetrics.widthPixels = 1080
            realMetrics.heightPixels = 2400
            realMetrics.densityDpi = DisplayMetrics.DENSITY_420
        }

        val width = realMetrics.widthPixels
        val height = realMetrics.heightPixels
        val rotation = defaultDisplay?.rotation ?: Surface.ROTATION_0
        val densityDpi = realMetrics.densityDpi
        val displayId = defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY

        val last = cachedGeometry
        val isValid = width > 0 && height > 0

        if (last == null ||
            last.screenshotWidth != width ||
            last.screenshotHeight != height ||
            last.rotation != rotation ||
            last.displayId != displayId ||
            last.isValid != isValid) {

            val newVersion = if (last == null) 1L else versionCounter.incrementAndGet()
            val newGeometry = WebStreamDisplayGeometry(
                displayId = displayId,
                screenshotWidth = width,
                screenshotHeight = height,
                inputWidth = width,
                inputHeight = height,
                rotation = rotation,
                densityDpi = densityDpi,
                geometryVersion = newVersion,
                isValid = isValid
            )
            cachedGeometry = newGeometry
            return newGeometry
        }

        return last
    }

    fun invalidateCache() {
        versionCounter.incrementAndGet()
        cachedGeometry = null
    }
}
