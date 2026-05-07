package com.example.trustsphere.data.remote.dto

data class VerificationResponse(
    val isSafe: Boolean,
    val message: String? = null
)