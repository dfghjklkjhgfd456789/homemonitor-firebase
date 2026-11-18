package com.google.android.system.termux

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PtyProcessHelper
 *
 * Handles subshell process creation, PTY stream handling,
 * execution timeouts, and ANSI output formatting.
 */
object PtyProcessHelper {
    private const val TAG = "PtyProcessHelper"

    private val ANSI_REGEX = Regex("\u001B\\[[;\\d]*[A-Za-z]|\u001B\\][^\u0007]*\u0007")

    data class ExecutionResult(
        val exitCode: Int,
        val output: String,
        val cleanOutput: String,
        val error: String? = null
    )

    /**
     * Executes a command string within the embedded Termux environment context.
     */
    fun executeCommand(
        context: Context,
        commandStr: String,
        workingDir: File? = null,
        timeoutMs: Long = 30000L
    ): ExecutionResult {
        val trimmedCmd = commandStr.trim()
        if (trimmedCmd.isEmpty()) {
            return ExecutionResult(
                exitCode = 0,
                output = "Empty command.",
                cleanOutput = "Empty command."
            )
        }

        return try {
            TermuxEnvironmentManager.initializeEnvironment(context)

            val shellPath = TermuxEnvironmentManager.getDefaultShellPath(context)
            val envMap = TermuxEnvironmentManager.buildEnvironmentMap(context)
            val targetDir = workingDir ?: TermuxEnvironmentManager.getHomeDir(context)

            val pb = if (shellPath.endsWith("busybox")) {
                ProcessBuilder(shellPath, "sh", "-c", trimmedCmd)
            } else {
                ProcessBuilder(shellPath, "-c", trimmedCmd)
            }

            pb.directory(targetDir)
            pb.environment().putAll(envMap)
            pb.redirectErrorStream(true)

            val process = pb.start()

            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputBuilder.append(line).append("\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reader thread error: ${e.message}")
                }
            }

            readerThread.start()

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                readerThread.interrupt()
                val partialRaw = outputBuilder.toString()
                val partialClean = stripAnsi(partialRaw)
                return ExecutionResult(
                    exitCode = -1,
                    output = "$partialRaw\n⚠️ Execution timed out after ${timeoutMs / 1000}s.",
                    cleanOutput = "$partialClean\n⚠️ Execution timed out after ${timeoutMs / 1000}s.",
                    error = "Timeout"
                )
            }

            readerThread.join(2000)

            val exitCode = process.exitValue()
            val rawOutput = outputBuilder.toString().trimEnd()
            val cleanOutput = stripAnsi(rawOutput)

            ExecutionResult(
                exitCode = exitCode,
                output = rawOutput,
                cleanOutput = cleanOutput
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command '$commandStr': ${e.message}", e)
            ExecutionResult(
                exitCode = -1,
                output = "Error: ${e.message}",
                cleanOutput = "Error: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Strips ANSI VT100 escape codes for clean Discord message rendering.
     */
    fun stripAnsi(input: String): String {
        return ANSI_REGEX.replace(input, "").trim()
    }
}
