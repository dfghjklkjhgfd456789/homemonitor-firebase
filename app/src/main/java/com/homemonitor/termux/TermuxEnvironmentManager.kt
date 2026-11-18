package com.google.android.system.termux

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * TermuxEnvironmentManager
 *
 * Manages the embedded Termux Linux userspace environment paths,
 * sandbox directories, bootstrap extraction, and environment variables.
 */
object TermuxEnvironmentManager {
    private const val TAG = "TermuxEnvManager"

    fun getFilesDir(context: Context): File = context.filesDir

    fun getPrefixDir(context: Context): File = File(getFilesDir(context), "usr")

    fun getHomeDir(context: Context): File = File(getFilesDir(context), "home")

    fun getTmpDir(context: Context): File = File(getPrefixDir(context), "tmp")

    fun getBinDir(context: Context): File = File(getPrefixDir(context), "bin")

    fun getLibDir(context: Context): File = File(getPrefixDir(context), "lib")

    fun getStagingDir(context: Context): File = File(getFilesDir(context), "usr-staging")

    /**
     * Prepares and initializes the Linux environment directory structure.
     */
    fun initializeEnvironment(context: Context): Boolean {
        return try {
            val prefix = getPrefixDir(context)
            val home = getHomeDir(context)
            val tmp = getTmpDir(context)
            val bin = getBinDir(context)
            val lib = getLibDir(context)

            val dirs = listOf(prefix, home, tmp, bin, lib)
            dirs.forEach { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            extractBootstrapAssetsIfNeeded(context)
            Log.i(TAG, "Termux environment initialized at: ${prefix.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Termux environment: ${e.message}", e)
            false
        }
    }

    /**
     * Builds standard Termux environment variables map for subprocess execution.
     */
    fun buildEnvironmentMap(context: Context): Map<String, String> {
        val prefix = getPrefixDir(context).absolutePath
        val home = getHomeDir(context).absolutePath
        val tmp = getTmpDir(context).absolutePath
        val bin = getBinDir(context).absolutePath
        val lib = getLibDir(context).absolutePath

        val systemPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val fullPath = "$bin:$systemPath"

        return mapOf(
            "TERM" to "xterm-256color",
            "HOME" to home,
            "PREFIX" to prefix,
            "TMPDIR" to tmp,
            "PATH" to fullPath,
            "LD_LIBRARY_PATH" to lib,
            "LANG" to "en_US.UTF-8",
            "TERMUX_VERSION" to "0.118.0",
            "TERMUX_MAIN_PACKAGE_FORMAT" to "debian"
        )
    }

    /**
     * Extracts optional bootstrap assets (e.g. busybox/sh binaries) if present in assets.
     */
    private fun extractBootstrapAssetsIfNeeded(context: Context) {
        val binDir = getBinDir(context)
        val busyboxFile = File(binDir, "busybox")

        if (!busyboxFile.exists()) {
            try {
                context.assets.open("busybox").use { input ->
                    FileOutputStream(busyboxFile).use { output ->
                        input.copyTo(output)
                    }
                }
                busyboxFile.setExecutable(true, false)
                Log.i(TAG, "Extracted busybox to: ${busyboxFile.absolutePath}")
            } catch (_: Exception) {
                // Asset not present; system sh/toybox will be used as default shell launcher
            }
        }
    }

    /**
     * Returns the preferred default shell binary path.
     */
    fun getDefaultShellPath(context: Context): String {
        val busybox = File(getBinDir(context), "busybox")
        val bash = File(getBinDir(context), "bash")

        return when {
            bash.exists() && bash.canExecute() -> bash.absolutePath
            busybox.exists() && busybox.canExecute() -> busybox.absolutePath
            else -> "/system/bin/sh"
        }
    }
}
