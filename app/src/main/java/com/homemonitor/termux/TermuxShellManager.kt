package com.google.android.system.termux

import android.content.Context
import android.util.Log
import java.io.File

/**
 * TermuxShellManager
 *
 * Primary controller for Termux subsystem session management,
 * command execution, and Discord I/O message formatting.
 */
object TermuxShellManager {
    private const val TAG = "TermuxShellManager"

    private const val DISCORD_MAX_LEN = 1900

    data class TermuxExecutionResponse(
        val success: Boolean,
        val exitCode: Int,
        val command: String,
        val formattedChunks: List<String>,
        val rawOutput: String
    )

    /**
     * Executes a shell command headlessly and returns formatted output chunks ready for Discord.
     */
    fun executeRemoteCommand(
        context: Context,
        commandStr: String,
        timeoutMs: Long = 30000L
    ): TermuxExecutionResponse {
        Log.i(TAG, "Executing remote Termux command: '$commandStr'")
        val result = PtyProcessHelper.executeCommand(context, commandStr, timeoutMs = timeoutMs)

        val isSuccess = result.exitCode == 0
        val outputText = if (result.cleanOutput.isNotBlank()) {
            result.cleanOutput
        } else {
            "✅ Command executed successfully (no output returned)."
        }

        val chunks = formatForDiscord(commandStr, result.exitCode, outputText)

        return TermuxExecutionResponse(
            success = isSuccess,
            exitCode = result.exitCode,
            command = commandStr,
            formattedChunks = chunks,
            rawOutput = result.output
        )
    }

    /**
     * Formats output string into one or more Discord-compliant markdown blocks (<= 2000 chars).
     */
    fun formatForDiscord(commandStr: String, exitCode: Int, outputText: String): List<String> {
        val header = "💻 **Termux Exec Result:** `!termux $commandStr` (exit code: $exitCode)\n```\n"
        val footer = "\n```"
        val availableLength = DISCORD_MAX_LEN - header.length - footer.length

        if (outputText.length <= availableLength) {
            return listOf("$header$outputText$footer")
        }

        val chunks = mutableListOf<String>()
        var remaining = outputText
        var page = 1

        while (remaining.isNotEmpty()) {
            val takeLength = minOf(remaining.length, availableLength - 30)
            val chunkText = remaining.substring(0, takeLength)
            remaining = remaining.substring(takeLength)

            val pageHeader = "💻 **Termux Result (Part $page):**\n```\n"
            chunks.add("$pageHeader$chunkText$footer")
            page++
        }

        return chunks
    }

    /**
     * Returns environment status summary for diagnosis.
     */
    fun getEnvironmentStatus(context: Context): String {
        val envInitialized = TermuxEnvironmentManager.initializeEnvironment(context)
        val prefix = TermuxEnvironmentManager.getPrefixDir(context).absolutePath
        val home = TermuxEnvironmentManager.getHomeDir(context).absolutePath
        val shell = TermuxEnvironmentManager.getDefaultShellPath(context)
        val binDir = TermuxEnvironmentManager.getBinDir(context)

        val bashExists = File(binDir, "bash").exists()
        val busyboxExists = File(binDir, "busybox").exists()
        val pkgExists = File(binDir, "pkg").exists() || File(binDir, "apt").exists()

        return """
            ✅ *Termux Subsystem Status*
            • Environment Status: ${if (envInitialized) "ACTIVE 🚀" else "ERROR ❌"}
            • Default Shell: `$shell`
            • Prefix Path: `$prefix`
            • Home Path: `$home`
            • Installed Binaries: Bash: ${if (bashExists) "✅" else "❌"}, Busybox: ${if (busyboxExists) "✅" else "❌"}, PKG/APT: ${if (pkgExists) "✅" else "❌"}
            • Operational Mode: Headless Discord Bidirectional I/O
        """.trimIndent()
    }
}
