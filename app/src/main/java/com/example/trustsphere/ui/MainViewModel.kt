package com.example.trustsphere.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trustsphere.data.local.ScanHistoryEntity
import com.example.trustsphere.data.model.AppRisk
import com.example.trustsphere.data.repository.AppRiskRepository
import com.example.trustsphere.data.repository.TrustRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrustRepository(application)
    private val appRiskRepository = AppRiskRepository(application)
    
    val scanHistory: StateFlow<List<ScanHistoryEntity>> = repository.scanHistory

    private val _verificationState = MutableStateFlow<VerificationStatus>(VerificationStatus.Idle)
    val verificationState: StateFlow<VerificationStatus> = _verificationState

    private val _riskyApps = mutableStateOf<List<AppRisk>>(emptyList())
    val riskyApps: State<List<AppRisk>> = _riskyApps

    fun verifyLink(url: String) {
        viewModelScope.launch {
            _verificationState.value = VerificationStatus.Loading
            repository.verifyLink(url).onSuccess { response ->
                if (response.isSafe) {
                    _verificationState.value = VerificationStatus.Safe(url)
                } else {
                    _verificationState.value = VerificationStatus.Unsafe(response.message ?: "This link might be malicious.")
                }
            }.onFailure { exception ->
                _verificationState.value = VerificationStatus.Error("Verification failed: ${exception.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun auditApps() {
        viewModelScope.launch {
            _riskyApps.value = appRiskRepository.getInstalledAppsRisk()
        }
    }

    fun clearHistory() {
        repository.clearHistory()
    }
    
    fun resetState() {
        _verificationState.value = VerificationStatus.Idle
    }
}

sealed class VerificationStatus {
    object Idle : VerificationStatus()
    object Loading : VerificationStatus()
    data class Safe(val url: String) : VerificationStatus()
    data class Unsafe(val message: String) : VerificationStatus()
    data class Error(val message: String) : VerificationStatus()
}