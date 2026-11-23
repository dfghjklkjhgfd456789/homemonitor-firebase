package com.homemonitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteCameraStudioTest {

    @Test
    fun testCameraActionCommandRouting() {
        // Test rear camera command routing
        val rearRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "camera.takePhoto",
            params = JSONObject().apply { put("lens", "back") },
            role = UserRole.PARENT
        )
        assertFalse("Without context, camera action reports requirement", rearRes.success)
        assertNotNull(rearRes.error)
        assertTrue(rearRes.error!!.contains("Context required"))

        // Test front camera command routing
        val frontRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "frontcam",
            params = JSONObject(),
            role = UserRole.PARENT
        )
        assertFalse(frontRes.success)
        assertTrue(frontRes.error!!.contains("Context required"))
    }

    @Test
    fun testPhotoTelemetryJsonFormatting() {
        val filename = "photo_rear_1700000000.jpg"
        val path = "/data/user/0/com.google.android.system/cache/$filename"
        val base64Data = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBD..."

        val photoObj = JSONObject().apply {
            put("filename", filename)
            put("path", path)
            put("sizeBytes", 102400L)
            put("imageData", base64Data)
            put("timestamp", 1700000000L)
        }

        assertEquals(filename, photoObj.getString("filename"))
        assertEquals(path, photoObj.getString("path"))
        assertTrue(photoObj.getString("imageData").startsWith("data:image/jpeg;base64,"))
        assertEquals(1700000000L, photoObj.getLong("timestamp"))
    }
}
