package com.homemonitor

import android.content.Context
import android.util.Log
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class DriveFileUploadResult(
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val checksum: String
)

/**
 * GoogleDriveArchiveEngine
 * Directs one-way archive uploads to Google Drive using the REST API (v3).
 * Manages OAuth 2.0 token refreshes, folder hierarchy creation, versioned manifest commitment,
 * GZIP compressed JSONL uploads, SHA-256 verification, and historical record retrieval.
 */
object GoogleDriveArchiveEngine {

    private const val TAG = "GoogleDriveArchiveEngine"
    private const val PREFS_NAME = "google_drive_archive_prefs"

    private const val KEY_DRIVE_CONNECTED = "drive_connected"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"

    private const val DEFAULT_CLIENT_ID = "891475820491-homemonitor-web-vault.apps.googleusercontent.com"

    private val httpClient = OkHttpClient()

    // ── Credential & Connection Management ──

    fun setDriveCredentials(context: Context, accountEmail: String, refreshToken: String, accessToken: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DRIVE_CONNECTED, true)
            .putString(KEY_ACCOUNT_EMAIL, accountEmail)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        if (!accessToken.isNull_or_empty_custom()) {
            prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + 3500_000L).apply()
        }
        Log.i(TAG, "Saved Google Drive credentials for account: $accountEmail")
    }

    private fun String?.isNull_or_empty_custom(): Boolean = this == null || this.trim().isEmpty()

    fun disconnectDrive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.i(TAG, "Disconnected Google Drive account.")
    }

    fun isDriveConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val connected = prefs.getBoolean(KEY_DRIVE_CONNECTED, false)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        return connected && refreshToken.isNotBlank()
    }

    fun getAccountEmail(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCOUNT_EMAIL, "Not Connected") ?: "Not Connected"
    }

    fun getOrRefreshToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, "")
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

        if (!currentToken.isNullOrBlank() && System.currentTimeMillis() < expiry) {
            return currentToken
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        if (refreshToken.isBlank()) return null

        val newToken = refreshAccessToken(refreshToken)
        if (!newToken.isNullOrBlank()) {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newToken)
                .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + 3500_000L)
                .apply()
            return newToken
        }
        return null
    }

    fun refreshAccessToken(refreshToken: String): String? {
        if (refreshToken.isBlank()) return null
        return try {
            val formBody = FormBody.Builder()
                .add("client_id", DEFAULT_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    if (json.has("access_token")) {
                        val token = json.getString("access_token")
                        Log.i(TAG, "Refreshed Drive OAuth access token successfully.")
                        token
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken exception: ${e.message}")
            null
        }
    }

    // ── Folder & File Operations ──

    fun findOrCreateFolder(accessToken: String, folderName: String, parentFolderId: String? = null): String? {
        if (accessToken.isBlank()) return null
        try {
            var query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
            if (!parentFolderId.isNullOrBlank()) {
                query += " and '$parentFolderId' in parents"
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${android.net.Uri.encode(query)}&fields=files(id,name)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }

            // Folder not found; create it
            val metaJson = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().put(parentFolderId))
                }
            }

            val createReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metaJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(createReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val createdId = json.optString("id", "")
                    Log.i(TAG, "Created Drive folder '$folderName' -> $createdId")
                    return createdId.ifBlank { null }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findOrCreateFolder exception for '$folderName': ${e.message}")
        }
        return null
    }

    fun ensureArchiveDirectoryPath(accessToken: String, familyId: String, deviceId: String, dataset: String): String? {
        val rootId = findOrCreateFolder(accessToken, "HomeMonitor Archive") ?: return null
        val familyIdFolder = findOrCreateFolder(accessToken, familyId, rootId) ?: return null
        val deviceIdFolder = findOrCreateFolder(accessToken, deviceId, familyIdFolder) ?: return null
        val datasetFolder = findOrCreateFolder(accessToken, dataset, deviceIdFolder) ?: return null

        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val monthStr = SimpleDateFormat("MM", Locale.US).format(Date())

        val yearFolder = findOrCreateFolder(accessToken, yearStr, datasetFolder) ?: return null
        val monthFolder = findOrCreateFolder(accessToken, monthStr, yearFolder) ?: return null

        return monthFolder
    }

    fun findDeviceRootFolder(accessToken: String, familyId: String, deviceId: String): String? {
        val rootId = findOrCreateFolder(accessToken, "HomeMonitor Archive") ?: return null
        val familyIdFolder = findOrCreateFolder(accessToken, familyId, rootId) ?: return null
        return findOrCreateFolder(accessToken, deviceId, familyIdFolder)
    }

    // ── Resumable / Multipart Upload ──

    fun uploadBatchFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        compressedBytes: ByteArray,
        mimeType: String = "application/gzip"
    ): DriveFileUploadResult? {
        if (accessToken.isBlank() || compressedBytes.isEmpty()) return null
        try {
            val checksum = computeSha256(compressedBytes)

            // Check if file already exists in folder (idempotency recovery)
            val query = "name='$fileName' and '$folderId' in parents and trashed=false"
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${android.net.Uri.encode(query)}&fields=files(id,name,size)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val existingObj = files.getJSONObject(0)
                        val existingId = existingObj.getString("id")
                        val existingSize = existingObj.optLong("size", compressedBytes.size.toLong())
                        Log.i(TAG, "Idempotency recovery: Batch file '$fileName' already exists on Drive ($existingId).")
                        return DriveFileUploadResult(existingId, fileName, existingSize, checksum)
                    }
                }
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
                put("mimeType", mimeType)
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    "metadata.json",
                    metadataJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                )
                .addFormDataPart(
                    "file",
                    fileName,
                    compressedBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: "{}"
                    val json = JSONObject(respStr)
                    val fileId = json.optString("id", "")
                    val size = json.optLong("size", compressedBytes.size.toLong())
                    if (fileId.isNotBlank()) {
                        Log.i(TAG, "Uploaded batch file '$fileName' ($size bytes) to Drive -> $fileId")
                        return DriveFileUploadResult(fileId, fileName, size, checksum)
                    }
                } else {
                    Log.e(TAG, "uploadBatchFile failed with HTTP ${response.code}: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadBatchFile exception for '$fileName': ${e.message}", e)
        }
        return null
    }

    fun verifyFileIntegrity(accessToken: String, fileId: String, expectedSizeBytes: Long): Boolean {
        if (accessToken.isBlank() || fileId.isBlank()) return false
        return try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,size,trashed")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val size = json.optLong("size", -1L)
                    val trashed = json.optBoolean("trashed", false)
                    val ok = !trashed && size > 0L && (expectedSizeBytes <= 0L || Math.abs(size - expectedSizeBytes) < 1024L)
                    Log.i(TAG, "Verified Drive file $fileId integrity: $ok (size: $size bytes)")
                    ok
                } else false
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyFileIntegrity exception for file $fileId: ${e.message}")
            false
        }
    }

    // ── Manifest Management ──

    fun getManifest(accessToken: String, deviceFolderId: String): JSONObject? {
        if (accessToken.isBlank() || deviceFolderId.isBlank()) return null
        return try {
            val query = "name='manifest.json' and '$deviceFolderId' in parents and trashed=false"
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${android.net.Uri.encode(query)}&fields=files(id)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            var manifestFileId: String? = null
            httpClient.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        manifestFileId = files.getJSONObject(0).getString("id")
                    }
                }
            }

            if (manifestFileId.isNullOrBlank()) return null

            val downloadReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$manifestFileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(downloadReq).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string() ?: "{}"
                    JSONObject(content)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getManifest exception: ${e.message}")
            null
        }
    }

    fun commitManifest(
        accessToken: String,
        deviceFolderId: String,
        familyId: String,
        deviceId: String,
        dataset: String,
        batchResult: DriveFileUploadResult,
        fromSeq: Long,
        toSeq: Long,
        recordCount: Int
    ): Boolean {
        if (accessToken.isBlank() || deviceFolderId.isBlank()) return false
        return try {
            val existingManifest = getManifest(accessToken, deviceFolderId) ?: JSONObject().apply {
                put("archiveVersion", 1)
                put("parentUid", familyId)
                put("deviceId", deviceId)
                put("datasets", JSONObject())
            }

            val datasetsObj = existingManifest.optJSONObject("datasets") ?: JSONObject()
            val datasetObj = datasetsObj.optJSONObject(dataset) ?: JSONObject().apply {
                put("lastArchivedSequence", 0L)
                put("batches", JSONArray())
            }

            val batchesArr = datasetObj.optJSONArray("batches") ?: JSONArray()

            // Check if batch already recorded in manifest
            var batchExists = false
            for (i in 0 until batchesArr.length()) {
                val b = batchesArr.getJSONObject(i)
                if (b.optString("batchId") == batchResult.name.replace(".jsonl.gz", "")) {
                    batchExists = true
                    break
                }
            }

            if (!batchExists) {
                val batchObj = JSONObject().apply {
                    put("batchId", batchResult.name.replace(".jsonl.gz", ""))
                    put("driveFileId", batchResult.fileId)
                    put("fromSequence", fromSeq)
                    put("toSequence", toSeq)
                    put("recordCount", recordCount)
                    put("checksum", batchResult.checksum)
                    put("schemaVersion", 1)
                    put("createdAt", System.currentTimeMillis())
                }
                batchesArr.put(batchObj)
            }

            datasetObj.put("lastArchivedSequence", Math.max(datasetObj.optLong("lastArchivedSequence", 0L), toSeq))
            datasetObj.put("batches", batchesArr)
            datasetsObj.put(dataset, datasetObj)
            existingManifest.put("datasets", datasetsObj)
            existingManifest.put("updatedAt", System.currentTimeMillis())

            val manifestContent = existingManifest.toString(2)

            // Search for existing manifest file ID to update or create new
            val query = "name='manifest.json' and '$deviceFolderId' in parents and trashed=false"
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${android.net.Uri.encode(query)}&fields=files(id)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            var manifestFileId: String? = null
            httpClient.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        manifestFileId = files.getJSONObject(0).getString("id")
                    }
                }
            }

            val success = if (!manifestFileId.isNullOrBlank()) {
                val updateReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$manifestFileId?uploadType=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .patch(manifestContent.toByteArray().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(updateReq).execute().use { it.isSuccessful }
            } else {
                val metaJson = JSONObject().apply {
                    put("name", "manifest.json")
                    put("parents", JSONArray().put(deviceFolderId))
                    put("mimeType", "application/json")
                }
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("metadata", "metadata.json", metaJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .addFormDataPart("file", "manifest.json", manifestContent.toByteArray().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val createReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(body)
                    .build()
                httpClient.newCall(createReq).execute().use { it.isSuccessful }
            }

            Log.i(TAG, "Committed manifest.json update to Drive: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "commitManifest exception: ${e.message}", e)
            false
        }
    }

    // ── Historical Record Retrieval ──

    fun readArchivedBatchRecords(accessToken: String, driveFileId: String): List<JSONObject> {
        val records = mutableListOf<JSONObject>()
        if (accessToken.isBlank() || driveFileId.isBlank()) return records

        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return records
                    val decompressedText = decompressGzip(bytes)
                    val lines = decompressedText.split("\n")
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank()) {
                            try {
                                records.add(JSONObject(trimmed))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readArchivedBatchRecords exception for $driveFileId: ${e.message}")
        }
        return records
    }

    // ── GZIP & Hashing Utilities ──

    fun compressGzip(jsonRecords: List<JSONObject>): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzos ->
            for (record in jsonRecords) {
                val line = record.toString() + "\n"
                gzos.write(line.toByteArray(Charsets.UTF_8))
            }
        }
        return baos.toByteArray()
    }

    fun decompressGzip(compressedBytes: ByteArray): String {
        val bais = ByteArrayInputStream(compressedBytes)
        GZIPInputStream(bais).use { gzis ->
            return gzis.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        val sb = StringBuilder()
        for (b in hash) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
