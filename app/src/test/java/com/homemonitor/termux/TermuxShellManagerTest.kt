package com.google.android.system.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxShellManagerTest {

    @Test
    fun testStripAnsiCodes() {
        val rawAnsi = "\u001B[31mError:\u001B[0m Process failed\u001B[1m!"
        val clean = PtyProcessHelper.stripAnsi(rawAnsi)
        assertEquals("Error: Process failed!", clean)
    }

    @Test
    fun testDiscordChunkingSmallOutput() {
        val command = "uname -a"
        val output = "Linux localhost 5.10.0-android #1 SMP PREEMPT"
        val chunks = TermuxShellManager.formatForDiscord(command, 0, output)

        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("uname -a"))
        assertTrue(chunks[0].contains("Linux localhost"))
    }

    @Test
    fun testDiscordChunkingLargeOutput() {
        val command = "pkg list"
        val largeOutput = "a".repeat(5000)
        val chunks = TermuxShellManager.formatForDiscord(command, 0, largeOutput)

        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertTrue(chunk.length <= 2000)
        }
    }
}
