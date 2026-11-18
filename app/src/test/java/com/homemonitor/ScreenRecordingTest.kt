package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenRecordingTest {

    @Test
    fun testScreenRecordingConstants() {
        assertEquals("extra_result_code", ScreenRecordingService.EXTRA_RESULT_CODE)
        assertEquals("extra_result_data", ScreenRecordingService.EXTRA_RESULT_DATA)
        assertEquals("extra_duration_sec", ScreenRecordingService.EXTRA_DURATION_SEC)
        assertEquals("extra_target_chat_id", ScreenRecordingService.EXTRA_TARGET_CHAT_ID)
    }

    @Test
    fun testInitialRecordingState() {
        assertFalse(ScreenRecordingService.isRecording)
        assertEquals(-1, ScreenRecordingService.cachedResultCode)
        assertNull(ScreenRecordingService.cachedProjectionIntent)
    }

    @Test
    fun testInvisibleActivityActionConstants() {
        assertEquals("action_screen_record", InvisibleActivity.ACTION_SCREEN_RECORD)
        assertEquals("action_photo", InvisibleActivity.ACTION_PHOTO)
        assertEquals("action_audio", InvisibleActivity.ACTION_AUDIO)
    }
}
