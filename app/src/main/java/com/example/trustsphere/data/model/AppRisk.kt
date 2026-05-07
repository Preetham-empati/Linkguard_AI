package com.example.trustsphere.data.model

import android.graphics.drawable.Drawable

data class AppRisk(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isHighRisk: Boolean,
    val riskyPermissions: List<String>
)