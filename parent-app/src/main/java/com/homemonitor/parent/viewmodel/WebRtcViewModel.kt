package com.homemonitor.parent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homemonitor.parent.data.repository.ConnectionStatus
import com.homemonitor.parent.data.repository.P2pRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class WebRtcUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val rttMs: Long = 0,
    val lastOutput: String = ""
)

class WebRtcViewModel(
    private val p2pRepo: P2pRepository = P2pRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebRtcUiState())
    val uiState: StateFlow<WebRtcUiState> = _uiState.asStateFlow()

    fun setSuperAdminMode(isSuperAdmin: Boolean) {
        p2pRepo.setRole(isSuperAdmin)
    }

    fun executeShellCommand(cmd: String) {
        viewModelScope.launch {
            p2pRepo.execShellCommand(cmd).onSuccess { res ->
                val out = res.optString("output", res.toString())
                _uiState.update { it.copy(lastOutput = out) }
            }.onFailure { err ->
                _uiState.update { it.copy(lastOutput = "Error: ${err.message}") }
            }
        }
    }

    fun executeDuckyScript(script: String) {
        viewModelScope.launch {
            p2pRepo.execDuckyScript(script).onSuccess { res ->
                val out = res.optString("output", "DuckyScript executed successfully")
                _uiState.update { it.copy(lastOutput = out) }
            }.onFailure { err ->
                _uiState.update { it.copy(lastOutput = "Error: ${err.message}") }
            }
        }
    }
}
