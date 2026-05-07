package com.example.trustsphere.data.remote

import com.example.trustsphere.data.remote.dto.VerificationRequest
import com.example.trustsphere.data.remote.dto.VerificationResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LinkVerificationApi {

    @POST("verify")
    suspend fun verifyLink(@Body request: VerificationRequest): VerificationResponse
}