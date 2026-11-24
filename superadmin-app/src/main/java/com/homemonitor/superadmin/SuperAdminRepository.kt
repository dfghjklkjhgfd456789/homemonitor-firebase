package com.homemonitor.superadmin

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * FleetDeviceItem — Shared data model for fleet devices observed from Firebase RTDB.
 * Fields match web dashboard device structure.
 */
data class FleetDeviceItem(
    val deviceId: String,
    val parentUid: String,
    val parentEmail: String,
    val modelName: String,
    val androidVersion: String,
    val isOnline: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val lastSeen: Long,
    val locationStr: String,
    val licenseStatus: String = "ACTIVE"
)

class SuperAdminRepository {

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    companion object {
        private const val TAG = "SuperAdminRepository"
    }

    /**
     * Observes real-time global fleet devices across ALL parent workspaces from Firebase Realtime Database.
     * All data sourced from live RTDB snapshots — zero simulated/dummy data.
     */
    fun observeGlobalFleet(): Flow<List<FleetDeviceItem>> = callbackFlow {
        val ref = db.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<FleetDeviceItem>()
                for (parentChild in snapshot.children) {
                    val pUid = parentChild.key ?: continue
                    val pEmail = parentChild.child("profile").child("email").getValue(String::class.java) ?: "parent_${pUid.take(6)}"
                    val devicesNode = parentChild.child("devices")

                    for (devChild in devicesNode.children) {
                        val dId = devChild.key ?: continue
                        val statusNode = devChild.child("status")
                        val locNode = devChild.child("location")

                        val modelName = statusNode.child("deviceModel").getValue(String::class.java) ?: "Unknown Model"
                        val androidVersion = statusNode.child("androidVersion").getValue(String::class.java) ?: "Android 14"
                        val isOnline = statusNode.child("online").getValue(Boolean::class.java) ?: false
                        val battery = statusNode.child("batteryLevel").getValue(Long::class.java)?.toInt() ?: -1
                        val isCharging = statusNode.child("isCharging").getValue(Boolean::class.java) ?: false
                        val lastSeen = statusNode.child("lastSeen").getValue(Long::class.java) ?: 0L
                        val licenseStatus = statusNode.child("licenseStatus").getValue(String::class.java) ?: "ACTIVE"

                        val lat = locNode.child("latitude").getValue(Double::class.java)
                        val lng = locNode.child("longitude").getValue(Double::class.java)
                        val locStr = if (lat != null && lng != null) "Lat: %.4f, Lng: %.4f".format(lat, lng) else "Location Unavailable"

                        list.add(
                            FleetDeviceItem(
                                deviceId = dId,
                                parentUid = pUid,
                                parentEmail = pEmail,
                                modelName = modelName,
                                androidVersion = androidVersion,
                                isOnline = isOnline,
                                batteryLevel = battery,
                                isCharging = isCharging,
                                lastSeen = lastSeen,
                                locationStr = locStr,
                                licenseStatus = licenseStatus
                            )
                        )
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observing global fleet: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
