package com.google.android.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.homemonitor.LocationTelemetryDto
import org.json.JSONObject

/**
 * LocationReportingManager
 *
 * Modular manager responsible for authorized GPS location verification,
 * location DTO formatting, and geofence safe zone perimeter checking.
 */
object LocationReportingManager {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun formatLocationTelemetryDto(location: Location): LocationTelemetryDto {
        return LocationTelemetryDto(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            accuracy = location.accuracy,
            provider = location.provider ?: "gps",
            timestamp = System.currentTimeMillis()
        )
    }

    fun isWithinGeofence(
        currentLat: Double,
        currentLng: Double,
        targetLat: Double,
        targetLng: Double,
        radiusMeters: Float
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(currentLat, currentLng, targetLat, targetLng, results)
        return results[0] <= radiusMeters
    }

    fun formatGeofenceAlertJson(
        zoneName: String,
        isInside: Boolean,
        currentLat: Double,
        currentLng: Double
    ): JSONObject {
        return JSONObject().apply {
            put("event", if (isInside) "GEOFENCE_ENTER" else "GEOFENCE_EXIT")
            put("zoneName", zoneName)
            put("isInside", isInside)
            put("latitude", currentLat)
            put("longitude", currentLng)
            put("timestamp", System.currentTimeMillis())
        }
    }
}
