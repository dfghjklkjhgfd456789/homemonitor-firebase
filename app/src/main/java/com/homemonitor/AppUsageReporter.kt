package com.google.android.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppUsageReporter
 *
 * Modular manager responsible for querying installed package information,
 * app labels, package names, and app telemetry formatting.
 */
object AppUsageReporter {

    data class AppPackageInfo(
        val appName: String,
        val packageName: String,
        val isSystemApp: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("appName", appName)
            put("packageName", packageName)
            put("isSystemApp", isSystemApp)
        }
    }

    fun getInstalledApplications(context: Context, onlyThirdParty: Boolean = false): List<AppPackageInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val result = mutableListOf<AppPackageInfo>()

        for (pkg in packages) {
            val isSystem = (pkg.applicationInfo != null && (pkg.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            if (onlyThirdParty && isSystem) continue

            val appName = try {
                pm.getApplicationLabel(pkg.applicationInfo!!).toString()
            } catch (_: Exception) {
                pkg.packageName
            }

            result.add(AppPackageInfo(appName, pkg.packageName, isSystem))
        }

        return result
    }

    fun getInstalledApplicationsJson(context: Context, onlyThirdParty: Boolean = false): JSONArray {
        val list = getInstalledApplications(context, onlyThirdParty)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr
    }
}
