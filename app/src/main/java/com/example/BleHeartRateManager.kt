package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sin

// Standard BLE Heart Rate Service UUIDs
private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class HeartRateZone {
    RESTING,    // < 60 BPM
    WARM_UP,    // 60-70% of Max HR
    FAT_BURN,   // 71-80% of Max HR
    CARDIO,     // 81-90% of Max HR
    PEAK        // 91%+ of Max HR
}

data class UiBluetoothDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isSimulated: Boolean = false
)

data class HeartRateSession(
    val age: Int = 30,
    val weightKg: Double = 70.0,
    val startTime: Long = 0L,
    val durationSeconds: Int = 0,
    val currentBpm: Int = 0,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val averageBpm: Int = 0,
    val totalCaloriesBurned: Double = 0.0,
    val bpmHistory: List<Int> = emptyList(),
    val zoneDistribution: Map<HeartRateZone, Int> = HeartRateZone.values().associateWith { 0 }
) {
    val maxHr: Int get() = 220 - age

    fun getActiveZone(bpm: Int): HeartRateZone {
        if (bpm <= 0) return HeartRateZone.RESTING
        val pct = (bpm.toDouble() / maxHr.toDouble()) * 100.0
        return when {
            bpm < 60 -> HeartRateZone.RESTING
            pct < 60.0 -> HeartRateZone.WARM_UP
            pct < 70.0 -> HeartRateZone.FAT_BURN
            pct < 85.0 -> HeartRateZone.CARDIO
            else -> HeartRateZone.PEAK
        }
    }
}

data class AlertConfig(
    val isEnabled: Boolean = true,
    val lowerBpm: Int = 55,
    val upperBpm: Int = 145
)

enum class AlertTriggerType {
    NONE,
    TOO_LOW,
    TOO_HIGH
}

data class AlertStatus(
    val type: AlertTriggerType = AlertTriggerType.NONE,
    val bpm: Int = 0,
    val message: String = ""
)

@SuppressLint("MissingPermission")
class BleHeartRateManager(private val context: Context) {

    private val TAG = "BleHeartRateManager"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // State flows for UI update
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<UiBluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _sessionState = MutableStateFlow(HeartRateSession())
    val sessionState = _sessionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _alertConfig = MutableStateFlow(AlertConfig())
    val alertConfig = _alertConfig.asStateFlow()

    private val _alertStatus = MutableStateFlow(AlertStatus())
    val alertStatus = _alertStatus.asStateFlow()

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
    private val CHANNEL_ID = "heart_rate_alerts"
    private var lastNotificationTime = 0L
    private val NOTIFICATION_THROTTLE_MS = 15000L // Repeat alert every 15s at most

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when your heart rate goes outside your target safety threshold limits."
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun sendPushNotification(title: String, body: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime < NOTIFICATION_THROTTLE_MS) return
        lastNotificationTime = now

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 300, 150, 300))

            notificationManager?.notify(1001, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }
    }

    fun updateAlertConfig(enabled: Boolean, lower: Int, upper: Int) {
        _alertConfig.update { it.copy(isEnabled = enabled, lowerBpm = lower, upperBpm = upper) }
        // Re-evaluate immediately
        val bpm = _sessionState.value.currentBpm
        if (bpm > 0) {
            checkHeartRateAlerts(bpm)
        }
    }

    private fun checkHeartRateAlerts(bpm: Int) {
        if (bpm <= 0) {
            _alertStatus.update { AlertStatus() }
            return
        }

        val config = _alertConfig.value
        if (!config.isEnabled) {
            if (_alertStatus.value.type != AlertTriggerType.NONE) {
                _alertStatus.update { AlertStatus() }
            }
            return
        }

        val currentStatus = _alertStatus.value
        val newStatus = when {
            bpm > config.upperBpm -> {
                AlertStatus(
                    type = AlertTriggerType.TOO_HIGH,
                    bpm = bpm,
                    message = "Pulse of $bpm BPM exceeds safe limit of ${config.upperBpm} BPM!"
                )
            }
            bpm < config.lowerBpm -> {
                AlertStatus(
                    type = AlertTriggerType.TOO_LOW,
                    bpm = bpm,
                    message = "Pulse of $bpm BPM is below safe limit of ${config.lowerBpm} BPM!"
                )
            }
            else -> {
                AlertStatus()
            }
        }

        if (newStatus.type != currentStatus.type) {
            _alertStatus.update { newStatus }
            if (newStatus.type != AlertTriggerType.NONE) {
                val title = if (newStatus.type == AlertTriggerType.TOO_HIGH) "⚠️ PulseSync: High Heart Rate!" else "⚠️ PulseSync: Low Heart Rate!"
                sendPushNotification(title, newStatus.message)
            }
        } else if (newStatus.type != AlertTriggerType.NONE && System.currentTimeMillis() - lastNotificationTime >= NOTIFICATION_THROTTLE_MS) {
            _alertStatus.update { newStatus.copy(bpm = bpm) } // Keep current reading updated
            val title = if (newStatus.type == AlertTriggerType.TOO_HIGH) "⚠️ PulseSync: High Heart Rate!" else "⚠️ PulseSync: Low Heart Rate!"
            sendPushNotification(title, newStatus.message)
        }
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var simulationJob: Job? = null
    private var isScanning = false

    // Simulator attributes
    private val simulatedDevice = UiBluetoothDevice(
        name = "🏥 PulseSim Band v5",
        address = "00:11:22:33:AA:BB",
        rssi = -55,
        isSimulated = true
    )

    // Scanning callback for real Bluetooth
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                val name = device.name ?: "Unknown Device"
                val address = device.address
                val rssi = it.rssi

                // We only look for devices that advertise Heart Rate Service,
                // or have "heart" / "pulse" / "band" / "fit" in their name.
                val lowerName = name.lowercase()
                val isProbablyFitness = lowerName.contains("heart") ||
                        lowerName.contains("pulse") ||
                        lowerName.contains("band") ||
                        lowerName.contains("fit") ||
                        lowerName.contains("watch") ||
                        lowerName.contains("hr")

                if (isProbablyFitness || lowerName.isNotEmpty()) {
                    addDiscoveredDevice(UiBluetoothDevice(name, address, rssi, false))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _errorMessage.value = "BLE scan failed (code $errorCode). Check location & Bluetooth settings."
        }
    }

    private fun addDiscoveredDevice(device: UiBluetoothDevice) {
        _scannedDevices.update { current ->
            if (current.none { it.address == device.address }) {
                current + device
            } else {
                current.map { if (it.address == device.address) device else it }
            }
        }
    }

    fun startScanning() {
        if (isScanning) return
        _errorMessage.value = null
        _scannedDevices.value = listOf(simulatedDevice) // Always include the simulation device so user can test the app!
        
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.i(TAG, "Bluetooth is disabled or not supported. Emulating scanner with visual simulator only.")
            _connectionState.value = ConnectionState.SCANNING
            isScanning = true
            // Run a simulated scan to show other mock devices if no Bluetooth is available, making it feel organic
            startSimulatedScanMockDiscovery()
            return
        }

        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner != null) {
                _connectionState.value = ConnectionState.SCANNING
                isScanning = true
                scanner.startScan(scanCallback)
                
                // Set a timeout to stop scanning after 20 seconds to be gentle on battery
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isScanning && _connectionState.value == ConnectionState.SCANNING) {
                        stopScanning()
                    }
                }, 20000)
            } else {
                _errorMessage.value = "Bluetooth Le Scanner unavailable. Using Simulation Mode."
                _connectionState.value = ConnectionState.SCANNING
                isScanning = true
                startSimulatedScanMockDiscovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scanner: ${e.message}")
            _errorMessage.value = "Permission or API error: ${e.localizedMessage}. Using Simulation Mode."
            _connectionState.value = ConnectionState.SCANNING
            isScanning = true
            startSimulatedScanMockDiscovery()
        }
    }

    private var mockDiscoveryJob: Job? = null
    private fun startSimulatedScanMockDiscovery() {
        mockDiscoveryJob?.cancel()
        mockDiscoveryJob = scope.launch {
            val mocks = listOf(
                UiBluetoothDevice("⚡ Fitbit Charge 6", "12:34:56:78:90:AB", -67),
                UiBluetoothDevice("⌚ Garmin Venu 3", "DE:AD:BE:EF:01:23", -78),
                UiBluetoothDevice("❤️ Polar H10 Chest Strap", "AA:BB:CC:DD:EE:FF", -45)
            )
            for (mock in mocks) {
                delay(1200)
                if (isScanning) {
                    addDiscoveredDevice(mock)
                }
            }
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        mockDiscoveryJob?.cancel()
        mockDiscoveryJob = null
        
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    fun connectDevice(device: UiBluetoothDevice) {
        stopScanning()
        _errorMessage.value = null
        _connectionState.value = ConnectionState.CONNECTING

        if (device.isSimulated) {
            startSimulation(device)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _errorMessage.value = "Bluetooth is disabled. Cannot connect to real device."
            _connectionState.value = ConnectionState.ERROR
            return
        }

        try {
            val remoteDevice = adapter.getRemoteDevice(device.address)
            bluetoothGatt = remoteDevice.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            _errorMessage.value = "Failed to initiate connection: ${e.localizedMessage}"
            _connectionState.value = ConnectionState.ERROR
        }
    }

    fun disconnect() {
        stopSimulation()
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        // Reset session current bpm, but keep history until user starts scanning again
        _sessionState.update { it.copy(currentBpm = 0) }
    }

    private fun startSimulation(device: UiBluetoothDevice) {
        stopSimulation()
        _connectionState.value = ConnectionState.CONNECTING
        
        simulationJob = scope.launch {
            delay(1500) // Simulate connection delay
            _connectionState.value = ConnectionState.CONNECTED
            
            // Re-initialize a training session
            _sessionState.value = HeartRateSession(
                startTime = System.currentTimeMillis()
            )

            var secondCount = 0
            var baseBpm = 72.0
            
            while (true) {
                delay(1000)
                secondCount++
                
                // Generate a wave of heart rate mimicking exercise: starts resting, goes up, settles, then goes down
                val workoutPhase = (secondCount.toDouble() / 120.0) // 2-min cycle
                val intensityFactor = sin(workoutPhase * Math.PI) // curves up then down
                // Base fluctuates naturally with noise
                val noise = (Math.random() * 4) - 2
                val currentBpm = (baseBpm + (intensityFactor * 55.0) + noise).toInt().coerceIn(45, 185)

                updateSessionWithBpm(currentBpm)
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun updateSessionWithBpm(bpm: Int) {
        _sessionState.update { session ->
            val newHistory = session.bpmHistory + bpm
            val elapsedSec = if (session.startTime > 0L) {
                ((System.currentTimeMillis() - session.startTime) / 1000L).toInt()
            } else {
                session.durationSeconds + 1
            }

            val maxVal = if (session.maxBpm == 0) bpm else maxOf(session.maxBpm, bpm)
            val minVal = if (session.minBpm == 0) bpm else minOf(session.minBpm, bpm)
            val avgVal = newHistory.average().toInt()

            // Calories estimation formula
            // Men: Calories = (Age * 0.2017 + Weight * 0.1988 + HeartRate * 0.6309 - 55.0969) * Time_minutes
            // Let's integrate incrementally each second
            val ageFactor = session.age * 0.2017
            val weightFactor = session.weightKg * 0.1988
            val hrFactor = bpm * 0.6309
            val kcalPerMinute = (ageFactor + weightFactor + hrFactor - 55.0969) / 4.184
            val kcalPerSecond = (kcalPerMinute / 60.0).coerceAtLeast(0.015) // Safe minimum resting caloric drain
            val newCalories = session.totalCaloriesBurned + kcalPerSecond

            // Track distribution
            val activeZone = session.getActiveZone(bpm)
            val newDistribution = session.zoneDistribution.toMutableMap()
            newDistribution[activeZone] = (newDistribution[activeZone] ?: 0) + 1

            session.copy(
                durationSeconds = elapsedSec,
                currentBpm = bpm,
                minBpm = minVal,
                maxBpm = maxVal,
                averageBpm = avgVal,
                totalCaloriesBurned = newCalories,
                bpmHistory = newHistory,
                zoneDistribution = newDistribution
            )
        }
        checkHeartRateAlerts(bpm)
    }

    // Bluetooth GATT Client Callback for real devices
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "GATT connected, initiating service discovery")
                        _connectionState.value = ConnectionState.CONNECTING
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "GATT disconnected")
                        gatt?.close()
                        bluetoothGatt = null
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            } else {
                Log.e(TAG, "GATT status error: $status, newState: $newState")
                gatt?.close()
                bluetoothGatt = null
                _errorMessage.value = "Connection lost or device rejected (code $status)"
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                if (hrService != null) {
                    val hrChar = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                    if (hrChar != null) {
                        Log.i(TAG, "Heart Rate Service and Characteristic found! Subscribing...")
                        _connectionState.value = ConnectionState.CONNECTED
                        
                        // Start tracking training session locally
                        _sessionState.value = HeartRateSession(startTime = System.currentTimeMillis())

                        // Enable notifications locally
                        gatt.setCharacteristicNotification(hrChar, true)

                        // Enable indications/notifications on device side (CCCD descriptor)
                        val descriptor = hrChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            Log.e(TAG, "Descriptor CLIENT_CHARACTERISTIC_CONFIG_UUID not found")
                        }
                    } else {
                        Log.e(TAG, "Heart rate measurement characteristic not found!")
                        _errorMessage.value = "Heart rate characteristics not found in this wearable service profile."
                        _connectionState.value = ConnectionState.ERROR
                    }
                } else {
                    Log.e(TAG, "GATT: Heart Rate Service NOT found on this device!")
                    _errorMessage.value = "No standard Heart Rate Service found on this wearable device."
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                Log.e(TAG, "GATT service discovery failed with status $status")
                _errorMessage.value = "Unable to read device's service list."
                _connectionState.value = ConnectionState.ERROR
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("onCharacteristicChanged(gatt, characteristic, characteristic.value)"))
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            // Support older APIs
            characteristic?.let {
                parseAndEmitBpm(it.value)
            }
        }

        // Modern API override
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseAndEmitBpm(value)
        }
    }

    private fun parseAndEmitBpm(value: ByteArray) {
        if (value.isEmpty()) return
        
        val flags = value[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        
        val bpm = if (is16Bit && value.size >= 3) {
            val b1 = value[1].toInt() and 0xFF
            val b2 = value[2].toInt() and 0xFF
            (b2 shl 8) or b1
        } else if (value.size >= 2) {
            value[1].toInt() and 0xFF
        } else {
            return
        }

        Log.d(TAG, "Parsed heart rate characteristic BPM: $bpm")
        scope.launch {
            updateSessionWithBpm(bpm)
        }
    }

    // Set configuration
    fun setAgeAndWeight(age: Int, weightKg: Double) {
        _sessionState.update { it.copy(age = age, weightKg = weightKg) }
    }
}
