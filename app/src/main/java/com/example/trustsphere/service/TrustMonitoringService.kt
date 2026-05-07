package com.example.trustsphere.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.example.trustsphere.MainActivity

class TrustMonitoringService : AccessibilityService() {

    private val paymentAppPackages = listOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                       // Paytm
        "com.freecharge.android",                // Freecharge
        "in.org.npci.upiapp"                     // BHIM
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (paymentAppPackages.contains(packageName)) {
                checkDeviceSafety()
            }
        }
    }

    private fun checkDeviceSafety() {
        val trustScore = calculateTrustScore()
        if (trustScore < 70) {
            showUnsafeEnvironmentWarning()
        }
    }

    private fun calculateTrustScore(): Int {
        var score = 100

        // Risk 1: Debugging Enabled (High Risk)
        if (Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) != 0) {
            score -= 30
        }

        // Risk 2: Other Accessibility Services Enabled (High Risk - can read screen/intercept taps)
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        // Check if there's any service enabled other than ourselves
        val otherServicesCount = enabledServices.count { 
            it.resolveInfo.serviceInfo.packageName != packageName 
        }
        if (otherServicesCount > 0) {
            score -= 40
        }

        // Risk 3: Developer Options (General risk)
        if (Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
            score -= 10
        }

        // Risk 4: Screen Overlay Permission granted to suspicious apps
        // (Simplified: we deduct if ANY app has overlay permission, except known safe ones)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            // In a production app, you'd iterate through apps with this permission.
            // score -= 10
        }
        
        return score
    }

    private fun showUnsafeEnvironmentWarning() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_UNSAFE_ENVIRONMENT", true)
        }
        startActivity(intent)
        
        Toast.makeText(this, "TrustSphere: Unsafe Environment Detected!", Toast.LENGTH_LONG).show()
    }

    override fun onInterrupt() {}
}