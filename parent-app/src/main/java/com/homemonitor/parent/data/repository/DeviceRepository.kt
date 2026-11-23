package com.homemonitor.parent.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class MonitoredDeviceItem(
    val deviceId: String,
    val parentUid: String,
    val model: String,
    val androidVersion: String,
    val isOnline: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val lastSeen: Long,
    val status: String
)

class DeviceRepository {

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    companion object {
        private const val TAG = "DeviceRepository"
    }

    fun observeParentDevices(parentUid: String): Flow<List<MonitoredDeviceItem>> = callbackFlow {
        val ref = db.getReference("users").child(parentUid).child("devices")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<MonitoredDeviceItem>()
                for (child in snapshot.children) {
                    val devId = child.key ?: continue
                    val statusNode = child.child("status")
                    val model = statusNode.child("deviceModel").getValue(String::class.java) ?: "Android Device"
                    val ver = statusNode.child("androidVersion").getValue(String::class.java) ?: "15"
                    val isOnline = statusNode.child("online").getValue(Boolean::class.java) ?: false
                    val battery = statusNode.child("batteryLevel").getValue(Long::class.java)?.toInt() ?: 100
                    val isCharging = statusNode.child("isCharging").getValue(Boolean::class.java) ?: false
                    val lastSeen = statusNode.child("lastSeen").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val stateStr = if (isOnline) "ONLINE" else "OFFLINE"

                    list.add(
                        MonitoredDeviceItem(
                            deviceId = devId,
                            parentUid = parentUid,
                            model = model,
                            androidVersion = ver,
                            isOnline = isOnline,
                            batteryLevel = battery,
                            isCharging = isCharging,
                            lastSeen = lastSeen,
                            status = stateStr
                        )
                    )
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error observing devices: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun pairDevice(parentUid: String, deviceId: String, model: String): Result<Boolean> {
        return try {
            val deviceRef = db.getReference("users").child(parentUid).child("devices").child(deviceId)
            val bindingRef = db.getReference("device_bindings").child(deviceId)

            val bindingData = mapOf(
                "deviceId" to deviceId,
                "parentUid" to parentUid,
                "status" to "APPROVED",
                "timestamp" to System.currentTimeMillis()
            )

            val statusData = mapOf(
                "deviceId" to deviceId,
                "deviceModel" to model,
                "online" to true,
                "batteryLevel" to 100,
                "isCharging" to true,
                "lastSeen" to System.currentTimeMillis()
            )

            bindingRef.setValue(bindingData).await()
            deviceRef.child("status").setValue(statusData).await()
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing error: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeGlobalFleet(): Flow<List<MonitoredDeviceItem>> = callbackFlow {
        val ref = db.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fleetList = mutableListOf<MonitoredDeviceItem>()
                for (parentChild in snapshot.children) {
                    val pUid = parentChild.key ?: continue
                    val devicesNode = parentChild.child("devices")
                    for (devChild in devicesNode.children) {
                        val dId = devChild.key ?: continue
                        val statusNode = devChild.child("status")
                        val model = statusNode.child("deviceModel").getValue(String::class.java) ?: "Target Device"
                        val ver = statusNode.child("androidVersion").getValue(String::class.java) ?: "15"
                        val isOnline = statusNode.child("online").getValue(Boolean::class.java) ?: false
                        val battery = statusNode.child("batteryLevel").getValue(Long::class.java)?.toInt() ?: 90
                        val isCharging = statusNode.child("isCharging").getValue(Boolean::class.java) ?: false
                        val lastSeen = statusNode.child("lastSeen").getValue(Long::class.java) ?: System.currentTimeMillis()

                        fleetList.add(
                            MonitoredDeviceItem(
                                deviceId = dId,
                                parentUid = pUid,
                                model = model,
                                androidVersion = ver,
                                isOnline = isOnline,
                                batteryLevel = battery,
                                isCharging = isCharging,
                                lastSeen = lastSeen,
                                status = if (isOnline) "ONLINE" else "OFFLINE"
                            )
                        )
                    }
                }
                trySend(fleetList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Global fleet database error: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
