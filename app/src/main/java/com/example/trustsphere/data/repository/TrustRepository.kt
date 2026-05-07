package com.example.trustsphere.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.trustsphere.data.local.ScanHistoryEntity
import com.example.trustsphere.data.remote.RetrofitInstance
import com.example.trustsphere.data.remote.dto.VerificationRequest
import com.example.trustsphere.data.remote.dto.VerificationResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrustRepository(context: Context) {
    private val api = RetrofitInstance.api
    private val prefs: SharedPreferences = context.getSharedPreferences("trust_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _scanHistory = MutableStateFlow<List<ScanHistoryEntity>>(loadHistory())
    val scanHistory: StateFlow<List<ScanHistoryEntity>> = _scanHistory.asStateFlow()

    suspend fun verifyLink(url: String): Result<VerificationResponse> {
        return try {
            val response = api.verifyLink(VerificationRequest(url))
            
            // Save to history
            val newEntry = ScanHistoryEntity(url, response.isSafe)
            val currentHistory = _scanHistory.value.toMutableList()
            currentHistory.add(0, newEntry) // Add to top
            
            saveHistory(currentHistory)
            _scanHistory.value = currentHistory
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveHistory(history: List<ScanHistoryEntity>) {
        val json = gson.toJson(history)
        prefs.edit().putString("scan_history", json).apply()
    }

    private fun loadHistory(): List<ScanHistoryEntity> {
        val json = prefs.getString("scan_history", null) ?: return emptyList()
        val type = object : TypeToken<List<ScanHistoryEntity>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearHistory() {
        prefs.edit().remove("scan_history").apply()
        _scanHistory.value = emptyList()
    }
}