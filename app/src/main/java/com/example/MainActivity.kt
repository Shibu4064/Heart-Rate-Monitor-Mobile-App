package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.border
import kotlin.OptIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HeartRateMonitorApp()
            }
        }
    }
}

object HeartRateZoneColors {
    fun getZoneColor(zone: HeartRateZone): Color {
        return when (zone) {
            HeartRateZone.RESTING -> Color(0xFF14B8A6)    // Soft Recovery Teal
            HeartRateZone.WARM_UP -> Color(0xFF3B82F6)    // Active Blue
            HeartRateZone.FAT_BURN -> Color(0xFFEAB308)   // Metabolic Yellow
            HeartRateZone.CARDIO -> Color(0xFFF97316)     // Endurance Orange
            HeartRateZone.PEAK -> Color(0xFFEF4444)       // Redline Peak
        }
    }

    fun getZoneName(zone: HeartRateZone): String {
        return when (zone) {
            HeartRateZone.RESTING -> "Recovery Zone"
            HeartRateZone.WARM_UP -> "Warm Up / Light"
            HeartRateZone.FAT_BURN -> "Fat Burning"
            HeartRateZone.CARDIO -> "Aerobic / Cardio"
            HeartRateZone.PEAK -> "Anaerobic / Peak"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateMonitorApp(
    viewModel: HeartRateViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.bleManager.connectionState.collectAsState()
    val discoveredDevices by viewModel.bleManager.scannedDevices.collectAsState()
    val session by viewModel.bleManager.sessionState.collectAsState()
    val errorMsg by viewModel.bleManager.errorMessage.collectAsState()
    val alertStatus by viewModel.bleManager.alertStatus.collectAsState()

    // Required Bluetooth permissions array based on Android API level
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val hasPermissions = remember(context) {
        permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            Toast.makeText(context, "Bluetooth scan permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.startScanning()
        } else {
            Toast.makeText(context, "Scanning requires permissions. Using simulator.", Toast.LENGTH_LONG).show()
            // Even if denied, a virtual device scanner is automatically initiated
            viewModel.startScanning()
        }
    }

    // Show error toast if we fetch one
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.isSettingsDialogOpen.value = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "User Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.shadow(2.dp)
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Settings info banner
                PhysiologicalInfoBar(
                    age = session.age,
                    weight = session.weightKg,
                    maxHr = session.maxHr,
                    onEditClick = { viewModel.isSettingsDialogOpen.value = true }
                )

                // In-app alert safety warning
                AnimatedVisibility(
                    visible = alertStatus.type != AlertTriggerType.NONE && connectionState == ConnectionState.CONNECTED,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AlertBannerCard(
                        alertStatus = alertStatus,
                        onAdjustClick = { viewModel.isSettingsDialogOpen.value = true }
                    )
                }

                AnimatedVisibility(
                    visible = connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.SCANNING,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DeviceScannerLayout(
                        connectionState = connectionState,
                        devices = discoveredDevices,
                        hasPermissions = hasPermissions,
                        onScanClick = {
                            if (permissionsToRequest.all {
                                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                }) {
                                viewModel.startScanning()
                            } else {
                                permissionLauncher.launch(permissionsToRequest)
                            }
                        },
                        onStopScanClick = { viewModel.stopScanning() },
                        onDeviceSelected = { viewModel.connectDevice(it) }
                    )
                }

                AnimatedVisibility(
                    visible = connectionState == ConnectionState.CONNECTING,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ConnectingCard(onCancelClick = { viewModel.disconnectDevice() })
                }

                AnimatedVisibility(
                    visible = connectionState == ConnectionState.CONNECTED,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ActiveMonitoringLayout(
                        session = session,
                        onDisconnectClick = { viewModel.disconnectDevice() }
                    )
                }

                AnimatedVisibility(
                    visible = connectionState == ConnectionState.ERROR,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ConnectionErrorCard(
                        errorText = errorMsg ?: "An unknown connection error occurred.",
                        onBackToScanClick = { viewModel.startScanning() }
                    )
                }
            }
        }
    }

    // Age / Weight / Alert Personalization Dialog
    if (viewModel.isSettingsDialogOpen.value) {
        SettingsDialog(
            ageValue = viewModel.ageInput.value,
            weightValue = viewModel.weightInput.value,
            onAgeChanged = { viewModel.ageInput.value = it },
            onWeightChanged = { viewModel.weightInput.value = it },
            alertsEnabled = viewModel.alertsEnabledInput.value,
            onAlertsEnabledChanged = { viewModel.alertsEnabledInput.value = it },
            lowerBpmValue = viewModel.lowerThresholdInput.value,
            onLowerBpmChanged = { viewModel.lowerThresholdInput.value = it },
            upperBpmValue = viewModel.upperThresholdInput.value,
            onUpperBpmChanged = { viewModel.upperThresholdInput.value = it },
            onSave = { viewModel.saveSettings() },
            onDismiss = { viewModel.isSettingsDialogOpen.value = false }
        )
    }
}

@Composable
fun PhysiologicalInfoBar(
    age: Int,
    weight: Double,
    maxHr: Int,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PHYSIOLOGICAL PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Age: $age yrs  •  Weight: ${weight}kg  •  Max HR: ${maxHr}bpm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Profile", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun DeviceScannerLayout(
    connectionState: ConnectionState,
    devices: List<UiBluetoothDevice>,
    hasPermissions: Boolean,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceSelected: (UiBluetoothDevice) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BLUETOOTH WEARABLES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connectionState == ConnectionState.SCANNING) "Locating cardiac sensors..." else "Select wearable sensor to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (connectionState == ConnectionState.SCANNING) {
                    Button(
                        onClick = onStopScanClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = onScanClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("scan_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Devices")
                    }
                }
            }

            if (connectionState == ConnectionState.SCANNING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            if (!hasPermissions && connectionState != ConnectionState.SCANNING) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "No Bluetooth BLE permission. Tap 'Scan Devices' to grant permission, or use the interactive Simulator Device shown below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (devices.isEmpty() && connectionState == ConnectionState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Looking for active heart rate peripherals...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (devices.isEmpty() && connectionState != ConnectionState.SCANNING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Wearable scanner ready",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Turn on your Bluetooth smart ring, watch, or chest strap. Click 'Scan Devices' to connect, or select our virtual high-accuracy wear simulator.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices) { device ->
                        DeviceItemRow(
                            device = device,
                            onClick = { onDeviceSelected(device) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItemRow(
    device: UiBluetoothDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("device_${device.address}"),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isSimulated) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = if (device.isSimulated) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isSimulated) Icons.Default.Favorite else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isSimulated) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "MAC: ${device.address}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.isSimulated) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SIMULATOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectingCard(
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Expanding radial halo
                val scale = remember { Animatable(1f) }
                val alpha = remember { Animatable(0.6f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        scale.snapTo(1f)
                        alpha.snapTo(0.6f)
                        launch { scale.animateTo(1.8f, animationSpec = tween(1200, easing = FastOutSlowInEasing)) }
                        launch { alpha.animateTo(0f, animationSpec = tween(1200, easing = FastOutSlowInEasing)) }
                        delay(1500)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            this.alpha = alpha.value
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Syncing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Establishing Connection...",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Requesting device handshakes & subscribing to Heart Rate GATT services",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCancelClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Cancel Handshake")
            }
        }
    }
}

@Composable
fun ConnectionErrorCard(
    errorText: String,
    onBackToScanClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connection Failed",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBackToScanClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("dismiss_error")
            ) {
                Text("Dismiss & Try Scanner")
            }
        }
    }
}

@Composable
fun ActiveMonitoringLayout(
    session: HeartRateSession,
    onDisconnectClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MONITOR ACTIVE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        OutlinedButton(
                            onClick = onDisconnectClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("disconnect_button")
                        ) {
                            Icon(imageVector = Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val activeZone = session.getActiveZone(session.currentBpm)
                    val zoneColor = HeartRateZoneColors.getZoneColor(activeZone)

                    // Beating Heart and Ripple
                    BeatingHeartUnit(currentBpm = session.currentBpm, activeZone = activeZone)

                    Spacer(modifier = Modifier.height(16.dp))

                    // BPM Large text
                    Text(
                        text = if (session.currentBpm > 0) "${session.currentBpm}" else "---",
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = if (session.currentBpm > 0) zoneColor else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "beats per minute",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Zone indicator badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = zoneColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = zoneColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = HeartRateZoneColors.getZoneName(activeZone).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = zoneColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "CARDIO STREAMING WAVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HeartRateCanvasGraph(
                            bpmHistory = session.bpmHistory,
                            primaryColor = HeartRateZoneColors.getZoneColor(session.getActiveZone(session.currentBpm))
                        )
                    }
                }
            }
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "WORKOUT METRICS SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Workout Time",
                        value = formatDuration(session.durationSeconds),
                        icon = Icons.Default.Timer,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active Burn",
                        value = String.format(Locale.getDefault(), "%.1f kcal", session.totalCaloriesBurned),
                        icon = Icons.Default.LocalFireDepartment,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Average Heart Rate",
                        value = if (session.averageBpm > 0) "${session.averageBpm} bpm" else "---",
                        icon = Icons.Default.CheckCircle,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Cardiac Min / Max",
                        value = if (session.minBpm > 0) "${session.minBpm}/${session.maxBpm}" else "---",
                        icon = Icons.Default.Favorite,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "HEART RATE ZONE FRACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val totalTicks = session.zoneDistribution.values.sum().coerceAtLeast(1)
                    val distributionPercent = HeartRateZone.values().associateWith { zone ->
                        val count = session.zoneDistribution[zone] ?: 0
                        (count.toDouble() / totalTicks.toDouble() * 100).toInt()
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeartRateZone.values().forEach { zone ->
                            val percent = distributionPercent[zone] ?: 0
                            val zColor = HeartRateZoneColors.getZoneColor(zone)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(zColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = HeartRateZoneColors.getZoneName(zone),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = { percent.toFloat() / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = zColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BeatingHeartUnit(
    currentBpm: Int,
    activeZone: HeartRateZone
) {
    val scale = remember { Animatable(1f) }
    
    // Animate heartbeat speed organically matching current BPM
    LaunchedEffect(currentBpm) {
        if (currentBpm > 0) {
            val beatIntervalMs = (60000L / currentBpm).coerceIn(320, 1500)
            while (true) {
                scale.animateTo(1.25f, animationSpec = tween(120, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, animationSpec = tween((beatIntervalMs - 120).toInt(), easing = LinearOutSlowInEasing))
            }
        } else {
            scale.snapTo(1.0f)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
    ) {
        val scaleWave = remember { Animatable(1f) }
        val alphaWave = remember { Animatable(0.4f) }
        
        LaunchedEffect(currentBpm) {
            if (currentBpm > 0) {
                val beatIntervalMs = (60000L / currentBpm).coerceIn(320, 1500)
                while (true) {
                    launch {
                        scaleWave.snapTo(1f)
                        alphaWave.snapTo(0.5f)
                        scaleWave.animateTo(1.7f, animationSpec = tween(beatIntervalMs.toInt(), easing = LinearEasing))
                    }
                    launch {
                        alphaWave.animateTo(0f, animationSpec = tween(beatIntervalMs.toInt(), easing = LinearEasing))
                    }
                    delay(beatIntervalMs)
                }
            }
        }

        // Ripple circular blur
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = scaleWave.value
                    scaleY = scaleWave.value
                    alpha = alphaWave.value
                }
                .background(
                    color = HeartRateZoneColors.getZoneColor(activeZone).copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )

        // Focal beating heart
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Pulse Heart Animation",
            tint = HeartRateZoneColors.getZoneColor(activeZone),
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                }
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
        )
    }
}

@Composable
fun HeartRateCanvasGraph(
    bpmHistory: List<Int>,
    primaryColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        val width = size.width
        val height = size.height

        if (bpmHistory.size > 1) {
            val maxHr = (bpmHistory.maxOrNull() ?: 100).coerceAtLeast(100)
            val minHr = (bpmHistory.minOrNull() ?: 60).coerceAtMost(60)
            val range = (maxHr - minHr).coerceAtLeast(15)

            // Display rolling window of last 40 beats
            val maxVisiblePoints = 40
            val points = bpmHistory.takeLast(maxVisiblePoints)
            val stepX = width / (points.size - 1).coerceAtLeast(1)

            val path = Path().apply {
                val startY = height - ((points[0] - minHr).toFloat() / range.toFloat() * height)
                moveTo(0f, startY)
                for (i in 1 until points.size) {
                    val x = i * stepX
                    val y = height - ((points[i] - minHr).toFloat() / range.toFloat() * height)
                    
                    // Smooth quadratic cubic bezier curve drawing
                    val prevX = (i - 1) * stepX
                    val prevY = height - ((points[i - 1] - minHr).toFloat() / range.toFloat() * height)
                    quadraticTo(prevX, prevY, (x + prevX) / 2f, (y + prevY) / 2f)
                }
                val lastX = (points.size - 1) * stepX
                val lastY = height - ((points.last() - minHr).toFloat() / range.toFloat() * height)
                lineTo(lastX, lastY)
            }

            // Fill shaded gradient
            val areaPath = Path().apply {
                addPath(path)
                val lastX = (points.size - 1) * stepX
                lineTo(lastX, height)
                lineTo(0f, height)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)
                )
            )

            // Heart Rate Line drawing
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Current heartbeat highlighted dot at the leading point
            val leadX = (points.size - 1) * stepX
            val leadY = height - ((points.last() - minHr).toFloat() / range.toFloat() * height)
            drawCircle(
                color = primaryColor,
                radius = 5.dp.toPx(),
                center = Offset(leadX, leadY)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = 11.dp.toPx(),
                center = Offset(leadX, leadY)
            )
        } else {
            // Placeholder flatline indicating sensor is syncing data
            val defaultY = height / 2f
            drawLine(
                color = primaryColor.copy(alpha = 0.4f),
                start = Offset(0f, defaultY),
                end = Offset(width, defaultY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(tint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SettingsDialog(
    ageValue: String,
    weightValue: String,
    onAgeChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    alertsEnabled: Boolean,
    onAlertsEnabledChanged: (Boolean) -> Unit,
    lowerBpmValue: String,
    onLowerBpmChanged: (String) -> Unit,
    upperBpmValue: String,
    onUpperBpmChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PHYSIOLOGICAL PROFILE",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Provide your anatomical credentials to calibrate target heart rate boundaries and dynamic calorie depletion algorithm accuracies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedTextField(
                    value = ageValue,
                    onValueChange = onAgeChanged,
                    label = { Text("Age (years)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("age_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = weightValue,
                    onValueChange = onWeightChanged,
                    label = { Text("Body Weight (kg)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("weight_input"),
                    singleLine = true
                )

                // Safe Threshold Area Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HEART RATE ALERTS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Alert immediately when heart rate flows outside the safe zone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Button(
                        onClick = { onAlertsEnabledChanged(!alertsEnabled) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alertsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("alerts_toggle_button")
                    ) {
                        Text(
                            text = if (alertsEnabled) "ENABLED" else "DISABLED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (alertsEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (alertsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = lowerBpmValue,
                            onValueChange = onLowerBpmChanged,
                            label = { Text("Min BPM") },
                            placeholder = { Text("55") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lower_bpm_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = upperBpmValue,
                            onValueChange = onUpperBpmChanged,
                            label = { Text("Max BPM") },
                            placeholder = { Text("145") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upper_bpm_input"),
                            singleLine = true
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text("Save Calibration")
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
fun AlertBannerCard(
    alertStatus: AlertStatus,
    onAdjustClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("in_app_alert_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert Warning Symbol",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SAFE RANGE BREACHED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = alertStatus.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAdjustClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Quick Calibration Adjustment",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
