package com.example

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleHeartRateManager(application)

    // Form inputs for accurate physiological metrics
    val ageInput = mutableStateOf("30")
    val weightInput = mutableStateOf("70")

    // Alert thresholds inputs
    val alertsEnabledInput = mutableStateOf(true)
    val lowerThresholdInput = mutableStateOf("55")
    val upperThresholdInput = mutableStateOf("145")
    
    // UI control states
    val isSettingsDialogOpen = mutableStateOf(false)

    init {
        // Apply initial configurations to the BLE manager
        applyPhysiologicalSettings()
        applyAlertSettings()
    }

    fun startScanning() {
        viewModelScope.launch {
            bleManager.startScanning()
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            bleManager.stopScanning()
        }
    }

    fun connectDevice(device: UiBluetoothDevice) {
        viewModelScope.launch {
            bleManager.connectDevice(device)
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            bleManager.disconnect()
        }
    }

    fun saveSettings() {
        applyPhysiologicalSettings()
        applyAlertSettings()
        isSettingsDialogOpen.value = false
    }

    private fun applyPhysiologicalSettings() {
        val ageVal = ageInput.value.toIntOrNull() ?: 30
        val weightVal = weightInput.value.toDoubleOrNull() ?: 70.0
        bleManager.setAgeAndWeight(ageVal, weightVal)
    }

    private fun applyAlertSettings() {
        val lowerBpm = lowerThresholdInput.value.toIntOrNull() ?: 55
        val upperBpm = upperThresholdInput.value.toIntOrNull() ?: 145
        bleManager.updateAlertConfig(alertsEnabledInput.value, lowerBpm, upperBpm)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
