package com.google.android.system.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class PackageInstallerHelper(private val context: Context) {

    companion object {
        const val ACTION_INSTALL_COMPLETE = "com.google.android.system.installer.ACTION_INSTALL_COMPLETE"
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    @Throws(IOException::class)
    fun installApk(apkFile: File): Int {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite("package", 0, apkFile.length()).use { outputStream ->
                FileInputStream(apkFile).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        outputStream.write(buffer, 0, len)
                    }
                    outputStream.flush()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        session.fsync(outputStream)
                    }
                }
            }

            val intent = Intent(ACTION_INSTALL_COMPLETE).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
            return sessionId
        } catch (e: Exception) {
            session.abandon()
            throw IOException("Failed to commit installation session: ${e.message}", e)
        }
    }
}
