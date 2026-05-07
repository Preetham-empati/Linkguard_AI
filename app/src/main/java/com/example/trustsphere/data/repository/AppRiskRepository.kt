package com.example.trustsphere.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.trustsphere.data.model.AppRisk

class AppRiskRepository(private val context: Context) {

    fun getInstalledAppsRisk(): List<AppRisk> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val riskyApps = mutableListOf<AppRisk>()

        for (app in apps) {
            // Skip system apps mostly, or include them if you want a thorough audit
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            val riskyPermissions = getRiskyPermissions(app.packageName)
            if (riskyPermissions.isNotEmpty()) {
                riskyApps.add(
                    AppRisk(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        icon = pm.getApplicationIcon(app),
                        isHighRisk = riskyPermissions.size >= 2,
                        riskyPermissions = riskyPermissions
                    )
                )
            }
        }
        return riskyApps.sortedByDescending { it.isHighRisk }
    }

    private fun getRiskyPermissions(packageName: String): List<String> {
        val riskyList = mutableListOf<String>()
        try {
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.forEach { permission ->
                when (permission) {
                    "android.permission.SYSTEM_ALERT_WINDOW" -> riskyList.add("Screen Overlay (Draw over apps)")
                    "android.permission.BIND_ACCESSIBILITY_SERVICE" -> riskyList.add("Accessibility Service")
                    "android.permission.READ_SMS" -> riskyList.add("Read SMS")
                    "android.permission.RECEIVE_SMS" -> riskyList.add("Receive SMS")
                    "android.permission.RECORD_AUDIO" -> riskyList.add("Microphone Access")
                    "android.permission.CAMERA" -> riskyList.add("Camera Access")
                }
            }
        } catch (e: Exception) {
            // App uninstalled or other error
        }
        return riskyList
    }
}