package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAIC2EngineTest {

    @Test
    fun testParseNaturalLanguageCameraCommands() {
        val intent1 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("take a camera photo")
        assertEquals("/camera", intent1.targetCommand)
        assertTrue(intent1.confidenceScore >= 0.90f)

        val intent2 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("take a selfie with front camera")
        assertEquals("/frontcam", intent2.targetCommand)
        assertTrue(intent2.confidenceScore >= 0.90f)

        val intent3 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("dump recent photos from device")
        assertEquals("/dumpphotos", intent3.targetCommand)
        assertEquals("10", intent3.extractedParameters)
    }

    @Test
    fun testParseNaturalLanguageLocationAndDeviceCommands() {
        val intent1 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("where is the device location")
        assertEquals("/geolocation", intent1.targetCommand)
        assertTrue(intent1.confidenceScore >= 0.90f)

        val intent2 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("check battery and online status")
        assertEquals("/status", intent2.targetCommand)
        assertTrue(intent2.confidenceScore >= 0.90f)

        val intent3 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("show device info and SIM telemetry")
        assertEquals("/deviceinfo", intent3.targetCommand)
    }

    @Test
    fun testParseNaturalLanguageAudioAndScreenCommands() {
        val intent1 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("take silent screenshot")
        assertEquals("/screenshot", intent1.targetCommand)

        val intent2 = OnDeviceAIC2Engine.parseNaturalLanguageCommand("record audio clip from ambient mic")
        assertEquals("/audio", intent2.targetCommand)
        assertEquals("15", intent2.extractedParameters)
    }

    @Test
    fun testParseDirectSlashCommands() {
        val intent = OnDeviceAIC2Engine.parseNaturalLanguageCommand("/termux uname -a")
        assertEquals("/termux", intent.targetCommand)
        assertEquals("uname -a", intent.extractedParameters)
        assertEquals(1.0f, intent.confidenceScore, 0.01f)
    }

    @Test
    fun testUnrecognizedQueryFallback() {
        val intent = OnDeviceAIC2Engine.parseNaturalLanguageCommand("random gibberish query 12345")
        assertEquals("/help", intent.targetCommand)
        assertFalse(intent.isRecognized)
    }

    @Test
    fun testSummarizeTelemetryStream() {
        val rawLogs = """
            [2026-11-12 10:00:00] App Opened: WhatsApp (com.whatsapp)
            [2026-11-12 10:05:00] App Opened: WhatsApp (com.whatsapp)
            [2026-11-12 10:10:00] App Opened: Telegram (org.telegram.messenger)
            [2026-11-12 10:15:00] App Opened: Chrome (com.android.chrome)
        """.trimIndent()

        val summary = OnDeviceAIC2Engine.summarizeTelemetryStream(rawLogs)
        assertTrue(summary.contains("AI Telemetry Stream Summary"))
        assertTrue(summary.contains("Total Log Entries"))
        assertTrue(summary.contains("WhatsApp"))
    }
}
