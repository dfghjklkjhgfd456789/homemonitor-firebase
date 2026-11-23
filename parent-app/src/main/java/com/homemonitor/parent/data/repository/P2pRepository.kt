package com.homemonitor.parent.data.repository

import android.util.Log
import com.homemonitor.parent.data.api.HomeMonitorApiClient
import com.homemonitor.parent.data.model.ParentRole
import org.json.JSONObject

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_P2P,
    CONNECTED_RELAY,
    FAILED
}

class P2pRepository {

    val apiClient = HomeMonitorApiClient()

    companion object {
        private const val TAG = "P2pRepository"
    }

    fun setRole(isSuperAdmin: Boolean) {
        apiClient.currentRole = if (isSuperAdmin) ParentRole.SUPER_ADMIN else ParentRole.PARENT
    }

    fun setSessionId(deviceId: String) {
        apiClient.activeSessionId = "sess_parent_${deviceId}_${System.currentTimeMillis()}"
    }

    suspend fun getDeviceSpecs(): Result<JSONObject> {
        return try {
            val data = apiClient.request("device.getSpecs")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceSpecs failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getCurrentLocation(): Result<JSONObject> {
        return try {
            val data = apiClient.request("location.getCurrent")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getScreenTimeSummary(): Result<JSONObject> {
        return try {
            val data = apiClient.request("usage.getSummary")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "getScreenTimeSummary failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun setAppBlockRules(blockedPackages: List<String>): Result<JSONObject> {
        return try {
            val params = JSONObject().apply {
                put("blockedPackages", org.json.JSONArray(blockedPackages))
            }
            val data = apiClient.request("usage.setBlockRules", params)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "setAppBlockRules failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getActivityLogs(category: String): Result<JSONObject> {
        return try {
            val params = JSONObject().apply { put("category", category) }
            val data = apiClient.request("logs.getFeed", params)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "getActivityLogs failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun lockDevice(): Result<JSONObject> {
        return try {
            val data = apiClient.request("device.lock")
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "lockDevice failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun execShellCommand(command: String): Result<JSONObject> {
        return try {
            val params = JSONObject().apply { put("command", command) }
            val data = apiClient.request("shell.exec", params)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "execShellCommand failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun execDuckyScript(scriptContent: String): Result<JSONObject> {
        return try {
            val params = JSONObject().apply { put("scriptContent", scriptContent) }
            val data = apiClient.request("ducky.execute", params)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "execDuckyScript failed: ${e.message}")
            Result.failure(e)
        }
    }
}
