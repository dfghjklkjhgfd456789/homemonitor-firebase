package com.homemonitor

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Manager responsible for harvesting application usage statistics via UsageStatsManager
 * and enforcing real-time application blocking rules.
 */
object AppUsageBlockerManager {
    private const val TAG = "AppUsageBlocker"
    private const val PREFS_NAME = "app_blocker_prefs"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeInForegroundMs: Long,
        val lastTimeUsedMs: Long
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves the set of currently blocked package names.
     */
    fun getBlockedPackages(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    /**
     * Updates the set of blocked package names.
     */
    fun setBlockedPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()
        Log.d(TAG, "Updated blocked packages count: ${packages.size}")
    }

    /**
     * Checks if a given package name is blocked.
     */
    fun isAppBlocked(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == context.packageName) return false
        val blocked = getBlockedPackages(context)
        return blocked.contains(packageName)
    }

    /**
     * Checks if PACKAGE_USAGE_STATS permission has been granted.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
        return stats != null && stats.isNotEmpty()
    }

    /**
     * Harvests application usage stats for the past 24 hours.
     */
    fun getAppUsageStats(context: Context): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = calendar.timeInMillis

        val usageStatsList: List<UsageStats>? = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return emptyList()
        }

        val pm = context.packageManager
        val result = mutableListOf<AppUsageInfo>()

        for (stats in usageStatsList) {
            if (stats.totalTimeInForeground > 0) {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(stats.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    stats.packageName
                }

                result.add(
                    AppUsageInfo(
                        packageName = stats.packageName,
                        appName = appName,
                        totalTimeInForegroundMs = stats.totalTimeInForeground,
                        lastTimeUsedMs = stats.lastTimeUsed
                    )
                )
            }
        }

        return result.sortedByDescending { it.totalTimeInForegroundMs }
    }

    /**
     * Exports usage stats to a JSON String for API reporting.
     */
    fun getAppUsageJson(context: Context): String {
        val stats = getAppUsageStats(context)
        val jsonArray = JSONArray()

        for (item in stats) {
            val obj = JSONObject().apply {
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("foregroundTimeMs", item.totalTimeInForegroundMs)
                put("lastTimeUsed", item.lastTimeUsedMs)
                put("isBlocked", isAppBlocked(context, item.packageName))
            }
            jsonArray.put(obj)
        }

        return jsonArray.toString()
    }

    /**
     * Triggers app blocking action when a restricted app appears in the foreground.
     */
    fun handleForegroundAppCheck(context: Context, packageName: String) {
        if (isAppBlocked(context, packageName)) {
            Log.w(TAG, "Blocked app detected in foreground: $packageName - Navigating to Home")
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch home intent for blocked app", e)
            }
        }
    }
}
