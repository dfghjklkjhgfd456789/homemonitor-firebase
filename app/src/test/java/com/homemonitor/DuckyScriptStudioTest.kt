package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckyScriptStudioTest {

    @Test
    fun testInitialStudioState() {
        assertFalse(DuckyScriptStudio.isRecordingMacro)
        assertEquals(0, DuckyScriptStudio.getRecordedLinesCount())
    }

    @Test
    fun testMacroRecordingLifecycleAndEventFormatting() {
        DuckyScriptStudio.startMacroRecording()
        assertTrue(DuckyScriptStudio.isRecordingMacro)

        DuckyScriptStudio.recordTapEvent(0.5f, 0.25f)
        DuckyScriptStudio.recordTextEvent("Hello World")
        DuckyScriptStudio.recordSwipeEvent(0.5f, 0.8f, 0.5f, 0.2f, 300L)

        assertTrue(DuckyScriptStudio.getRecordedLinesCount() >= 5)
    }

    @Test
    fun testValidDuckyScriptSyntaxValidation() {
        val validScript = """
            REM Test Ducky Script Payload
            DEFAULT_DELAY 200
            TAP 0.500 0.250
            STRING Hello World
            ENTER
            DELAY 500
            HOME
        """.trimIndent()

        val report = DuckyScriptStudio.validateScriptSyntax(validScript)
        assertTrue(report.isValid)
        assertEquals(0, report.errorLines.size)
        assertEquals(7, report.validCommandsCount)
    }

    @Test
    fun testInvalidDuckyScriptSyntaxValidation() {
        val invalidScript = """
            REM Test Invalid Script
            UNKNOWN_COMMAND_123 abc
            STRING Hello World
            INVALID_STATEMENT_XYZ
        """.trimIndent()

        val report = DuckyScriptStudio.validateScriptSyntax(invalidScript)
        assertFalse(report.isValid)
        assertEquals(2, report.errorLines.size)
    }
}
