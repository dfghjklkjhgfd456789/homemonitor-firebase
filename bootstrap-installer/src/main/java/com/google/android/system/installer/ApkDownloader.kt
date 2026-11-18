package com.google.android.system.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApkDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class DownloadProgress(
        val bytesRead: Long,
        val totalBytes: Long,
        val progressPercent: Int,
        val isCompleted: Boolean
    )

    @Throws(IOException::class, SecurityException::class)
    suspend fun downloadApk(
        urlString: String,
        onProgress: (DownloadProgress) -> kotlin.Unit,
        isCancelled: () -> Boolean
    ): File = withContext(Dispatchers.IO) {
        if (!urlString.startsWith("https://", ignoreCase = true)) {
            throw SecurityException("HTTPS is strictly required for downloading APKs.")
        }

        val targetFile = File(context.filesDir, "main-app.apk")
        val partFile = File(context.filesDir, "main-app.apk.part")

        if (partFile.exists()) {
            partFile.delete()
        }

        var downloadedBytes = 0.0
        var totalBytes = -1L

        val requestBuilder = Request.Builder().url(urlString)
        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            throw IOException("Server returned HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        totalBytes = body.contentLength()

        body.byteStream().use { inputStream ->
            FileOutputStream(partFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    ensureActive()
                    if (isCancelled()) {
                        outputStream.close()
                        partFile.delete()
                        throw IOException("Download cancelled by user.")
                    }
                    outputStream.write(buffer, 0, bytes)
                    downloadedBytes += bytes

                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes / totalBytes) * 100).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }

                    onProgress(
                        DownloadProgress(
                            bytesRead = downloadedBytes.toLong(),
                            totalBytes = totalBytes,
                            progressPercent = percent,
                            isCompleted = false
                        )
                    )
                }
                outputStream.flush()
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        if (!partFile.renameTo(targetFile)) {
            throw IOException("Failed to finalize downloaded APK file.")
        }

        onProgress(
            DownloadProgress(
                bytesRead = targetFile.length(),
                totalBytes = targetFile.length(),
                progressPercent = 100,
                isCompleted = true
            )
        )

        return@withContext targetFile
    }

    fun cleanup() {
        val partFile = File(context.filesDir, "main-app.apk.part")
        if (partFile.exists()) {
            partFile.delete()
        }
    }
}
