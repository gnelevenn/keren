package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val gamepadState = GamepadState()
    private lateinit var hidManager: BluetoothHidManager
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing games
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set up immersive sticky fullscreen mode
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Setup Vibrator for low-latency haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        hidManager = BluetoothHidManager(this, gamepadState)

        setContent {
            MyApplicationTheme {
                MainScreen(
                    hidManager = hidManager,
                    gamepadState = gamepadState,
                    onHapticFeedback = { triggerHapticFeedback() },
                    onRequestEnableBluetooth = { requestEnableBluetooth() },
                    onRequestDiscoverable = { requestDiscoverable() }
                )
            }
        }
    }

    private fun triggerHapticFeedback() {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Light micro-haptic click pulse (15ms)
                val effect = VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                vib.vibrate(effect)
            } catch (e: Exception) {
                // Fallback
            }
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(15)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivity(enableBtIntent)
    }

    @SuppressLint("MissingPermission")
    private fun requestDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
    }

    override fun onResume() {
        super.onResume()
        // Re-hide system bars when returning from dialogs or intents
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        hidManager.refreshPairedHosts()
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.release()
    }
}

@Composable
fun MainScreen(
    hidManager: BluetoothHidManager,
    gamepadState: GamepadState,
    onHapticFeedback: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onRequestDiscoverable: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by hidManager.status.collectAsState()
    val pairedHosts by hidManager.pairedHosts.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }
    var hasPermissions by remember {
        mutableStateOf(checkRequiredPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        hasPermissions = allGranted
        if (allGranted) {
            hidManager.initialize()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissions = getRequiredPermissions()
            permissionLauncher.launch(permissions)
        } else {
            hidManager.initialize()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F1116))
        ) {
            // SLIM QUICK PAIRING BAR AT TOP
            QuickPairingHeader(
                connectionStatus = connectionStatus,
                hasPermissions = hasPermissions,
                onRequestPermissions = {
                    permissionLauncher.launch(getRequiredPermissions())
                },
                onRequestDiscoverable = onRequestDiscoverable,
                onRequestEnableBluetooth = onRequestEnableBluetooth,
                onOpenPairingDialog = {
                    hidManager.refreshPairedHosts()
                    showPairingDialog = true
                },
                onDisconnect = {
                    hidManager.disconnectHost()
                }
            )

            // DUALSENSE INTERACTIVE GAMEPAD VIEW
            DualSenseControllerView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gamepadState = gamepadState,
                onStateChanged = {
                    hidManager.sendReportImmediate()
                },
                onHapticFeedback = onHapticFeedback
            )
        }

        // Host Selection Dialog
        if (showPairingDialog) {
            PairedHostsDialog(
                pairedHosts = pairedHosts,
                onDismiss = { showPairingDialog = false },
                onSelectDevice = { device ->
                    showPairingDialog = false
                    hidManager.connectToHost(device)
                },
                onRefresh = { hidManager.refreshPairedHosts() },
                onMakeDiscoverable = onRequestDiscoverable
            )
        }
    }
}

/**
 * Slim top bar displaying live Bluetooth HID status, host connection info, and quick pairing actions.
 */
@Composable
fun QuickPairingHeader(
    connectionStatus: BluetoothHidManager.ConnectionStatus,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDiscoverable: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onOpenPairingDialog: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        color = Color(0xFF161920),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Indicator & Description
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (connectionStatus) {
                    is BluetoothHidManager.ConnectionStatus.Connected -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38EF7D))
                        )
                        Icon(
                            imageVector = Icons.Filled.BluetoothConnected,
                            contentDescription = "Connected",
                            tint = Color(0xFF38EF7D),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Terhubung: ${connectionStatus.deviceName}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    is BluetoothHidManager.ConnectionStatus.WaitingForHost -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB703))
                        )
                        Icon(
                            imageVector = Icons.Filled.BluetoothSearching,
                            contentDescription = "Waiting",
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Menunggu Host (Siap Pairing Bluetooth)",
                            color = Color(0xFFFFB703),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is BluetoothHidManager.ConnectionStatus.Registering -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                        Text(
                            text = "Menginisialisasi HID Profile...",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp
                        )
                    }

                    is BluetoothHidManager.ConnectionStatus.BluetoothDisabled -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252))
                        )
                        Text(
                            text = "Bluetooth Tidak Aktif",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is BluetoothHidManager.ConnectionStatus.HidNotSupported -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252))
                        )
                        Text(
                            text = "Bluetooth HID Device Tidak Didukung Perangkat Ini",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is BluetoothHidManager.ConnectionStatus.Error -> {
                        Text(
                            text = connectionStatus.message,
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Quick Actions on Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasPermissions) {
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("request_permission_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Izinkan Bluetooth", fontSize = 10.sp, color = Color.White)
                    }
                } else if (connectionStatus is BluetoothHidManager.ConnectionStatus.BluetoothDisabled) {
                    Button(
                        onClick = onRequestEnableBluetooth,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("enable_bluetooth_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Aktifkan Bluetooth", fontSize = 10.sp, color = Color.Black)
                    }
                } else if (connectionStatus is BluetoothHidManager.ConnectionStatus.Connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("disconnect_host_button"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Putuskan", fontSize = 10.sp, color = Color(0xFFFF5252))
                    }
                } else {
                    // Waiting for host actions
                    Button(
                        onClick = onRequestDiscoverable,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282D38)),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("make_discoverable_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Mode Discoverable", fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }

                    Button(
                        onClick = onOpenPairingDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("paired_hosts_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = "Hosts",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Pilih Host", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog displaying paired host devices for instant connection.
 */
@Composable
fun PairedHostsDialog(
    pairedHosts: List<BluetoothDevice>,
    onDismiss: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    onMakeDiscoverable: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sambungkan ke Host PC / Konsol",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF00E5FF)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pilih perangkat PC, Laptop, atau Android TV yang sudah terpasang (paired), atau aktifkan Mode Discoverable untuk menghubungkan perangkat baru:",
                    fontSize = 12.sp,
                    color = Color(0xFFB0B7C3)
                )

                if (pairedHosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada perangkat terpasang.\nAktifkan Mode Discoverable lalu pasangkan dari pengaturan Bluetooth di PC / Konsol Anda.",
                            fontSize = 12.sp,
                            color = Color(0xFF8A92A0)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pairedHosts) { device ->
                            @SuppressLint("MissingPermission")
                            val deviceName = try {
                                device.name ?: device.address
                            } catch (e: Exception) {
                                device.address
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222630))
                                    .clickable { onSelectDevice(device) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Devices,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = deviceName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = device.address,
                                            color = Color(0xFF8A92A0),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "Hubungkan",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onMakeDiscoverable,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3544)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Jadikan HP Discoverable (300 detik)", color = Color(0xFF00E5FF), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF181B22)
    )
}

/**
 * Checks whether all required Bluetooth permissions are granted.
 */
private fun checkRequiredPermissions(context: Context): Boolean {
    val permissions = getRequiredPermissions()
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Returns required permissions based on Android API version.
 */
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.VIBRATE
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.VIBRATE
        )
    }
}
