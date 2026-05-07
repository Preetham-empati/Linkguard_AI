package com.example.trustsphere.data.local

data class ScanHistoryEntity(
    val url: String,
    val isSafe: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)