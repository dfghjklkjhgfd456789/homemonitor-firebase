package com.homemonitor.parent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homemonitor.parent.data.repository.DeviceRepository
import com.homemonitor.parent.data.repository.MonitoredDeviceItem
import com.homemonitor.parent.data.repository.P2pRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

data class DeviceUiState(
    val devices: List<MonitoredDeviceItem> = emptyList(),
    val globalFleet: List<MonitoredDeviceItem> = emptyList(),
    val selectedDeviceId: String? = null,
    val activeDeviceSpecs: JSONObject? = null,
    val currentLocation: JSONObject? = null,
    val screenTimeSummary: JSONObject? = null,
    val activityLogs: List<JSONObject> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DeviceViewModel(
    private val deviceRepo: DeviceRepository = DeviceRepository(),
    private val p2pRepo: P2pRepository = P2pRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    fun observeUserDevices(parentUid: String) {
        viewModelScope.launch {
            deviceRepo.observeParentDevices(parentUid).collect { deviceList ->
                _uiState.update { state ->
                    val selected = state.selectedDeviceId ?: deviceList.firstOrNull()?.deviceId
                    state.copy(devices = deviceList, selectedDeviceId = selected)
                }
            }
        }
    }

    fun observeGlobalFleet() {
        viewModelScope.launch {
            deviceRepo.observeGlobalFleet().collect { fleetList ->
                _uiState.update { it.copy(globalFleet = fleetList) }
            }
        }
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
        p2pRepo.setSessionId(deviceId)
        refreshDeviceData()
    }

    fun refreshDeviceData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            p2pRepo.getDeviceSpecs().onSuccess { specs ->
                _uiState.update { it.copy(activeDeviceSpecs = specs) }
            }

            p2pRepo.getCurrentLocation().onSuccess { loc ->
                _uiState.update { it.copy(currentLocation = loc) }
            }

            p2pRepo.getScreenTimeSummary().onSuccess { usage ->
                _uiState.update { it.copy(screenTimeSummary = usage) }
            }

            p2pRepo.getActivityLogs("notifications").onSuccess { logs ->
                val entries = mutableListOf<JSONObject>()
                val arr = logs.optJSONArray("entries")
                if (arr != null) {
                    for (i in 0 until arr.length()) entries.add(arr.getJSONObject(i))
                }
                _uiState.update { it.copy(activityLogs = entries) }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setAppBlockRules(blockedPkgs: List<String>) {
        viewModelScope.launch {
            p2pRepo.setAppBlockRules(blockedPkgs).onSuccess {
                refreshDeviceData()
            }
        }
    }

    fun lockActiveDevice() {
        viewModelScope.launch {
            p2pRepo.lockDevice()
        }
    }

    fun pairNewDevice(parentUid: String, deviceId: String, model: String) {
        viewModelScope.launch {
            deviceRepo.pairDevice(parentUid, deviceId, model).onSuccess {
                observeUserDevices(parentUid)
            }
        }
    }
}
