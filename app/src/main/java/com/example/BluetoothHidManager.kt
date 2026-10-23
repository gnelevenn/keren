package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Manages the Bluetooth HID Device profile, SDP registration, and real-time
 * zero-allocation report transmission for the PS5 DualSense virtual gamepad.
 */
class BluetoothHidManager(
    private val context: Context,
    val gamepadState: GamepadState
) {
    companion object {
        private const val TAG = "BluetoothHidManager"
        private const val REPORT_ID_GAMEPAD: Byte = 1
        private const val DISPATCH_INTERVAL_MS = 10L // ~100Hz polling rate for sub-frame response

        // Standard USB HID Gamepad Report Descriptor (9-byte input payload with Report ID 1)
        val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),        // Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x05.toByte(),        // Usage (Game Pad)
            0xa1.toByte(), 0x01.toByte(),        // Collection (Application)
            0xa1.toByte(), 0x00.toByte(),        //   Collection (Physical)
            0x85.toByte(), REPORT_ID_GAMEPAD,   //     Report ID (1)

            // 16 Action & Shoulder Buttons (Bytes 0 & 1)
            0x05.toByte(), 0x09.toByte(),        //     Usage Page (Button)
            0x19.toByte(), 0x01.toByte(),        //     Usage Minimum (1)
            0x29.toByte(), 0x10.toByte(),        //     Usage Maximum (16)
            0x15.toByte(), 0x00.toByte(),        //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),        //     Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(),        //     Report Size (1 bit)
            0x95.toByte(), 0x10.toByte(),        //     Report Count (16 buttons)
            0x81.toByte(), 0x02.toByte(),        //     Input (Data,Var,Abs)

            // D-Pad / Hat Switch (Byte 2 - 4 bits + 4 bits padding)
            0x05.toByte(), 0x01.toByte(),        //     Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x39.toByte(),        //     Usage (Hat switch)
            0x15.toByte(), 0x01.toByte(),        //     Logical Minimum (1)
            0x25.toByte(), 0x08.toByte(),        //     Logical Maximum (8)
            0x35.toByte(), 0x00.toByte(),        //     Physical Minimum (0)
            0x46.toByte(), 0x3b.toByte(), 0x01.toByte(), // Physical Maximum (315)
            0x65.toByte(), 0x14.toByte(),        //     Unit (Eng Rot:Angular Pos)
            0x75.toByte(), 0x04.toByte(),        //     Report Size (4 bits)
            0x95.toByte(), 0x01.toByte(),        //     Report Count (1)
            0x81.toByte(), 0x42.toByte(),        //     Input (Data,Var,Abs,Null)
            // 4-bit padding to maintain byte alignment
            0x75.toByte(), 0x04.toByte(),        //     Report Size (4 bits)
            0x95.toByte(), 0x01.toByte(),        //     Report Count (1)
            0x81.toByte(), 0x03.toByte(),        //     Input (Const,Var,Abs)

            // Dual Analog Thumbsticks: X, Y, Z, Rz (Bytes 3..6)
            0x05.toByte(), 0x01.toByte(),        //     Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x30.toByte(),        //     Usage (X) - Left Stick X
            0x09.toByte(), 0x31.toByte(),        //     Usage (Y) - Left Stick Y
            0x09.toByte(), 0x32.toByte(),        //     Usage (Z) - Right Stick X
            0x09.toByte(), 0x35.toByte(),        //     Usage (Rz) - Right Stick Y
            0x15.toByte(), 0x00.toByte(),        //     Logical Minimum (0)
            0x26.toByte(), 0xff.toByte(), 0x00.toByte(), // Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),        //     Report Size (8 bits)
            0x95.toByte(), 0x04.toByte(),        //     Report Count (4 axes)
            0x81.toByte(), 0x02.toByte(),        //     Input (Data,Var,Abs)

            // Analog Triggers: Rx (L2) & Ry (R2) (Bytes 7 & 8)
            0x05.toByte(), 0x01.toByte(),        //     Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x33.toByte(),        //     Usage (Rx) - L2 Trigger
            0x09.toByte(), 0x34.toByte(),        //     Usage (Ry) - R2 Trigger
            0x15.toByte(), 0x00.toByte(),        //     Logical Minimum (0)
            0x26.toByte(), 0xff.toByte(), 0x00.toByte(), // Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),        //     Report Size (8 bits)
            0x95.toByte(), 0x02.toByte(),        //     Report Count (2 axes)
            0x81.toByte(), 0x02.toByte(),        //     Input (Data,Var,Abs)

            0xc0.toByte(),                      //   End Collection
            0xc0.toByte()                       // End Collection
        )
    }

    sealed class ConnectionStatus {
        object BluetoothDisabled : ConnectionStatus()
        object HidNotSupported : ConnectionStatus()
        object Registering : ConnectionStatus()
        object WaitingForHost : ConnectionStatus()
        data class Connected(val deviceName: String, val address: String) : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null
    private var isAppRegistered = false

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Registering)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _pairedHosts = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedHosts: StateFlow<List<BluetoothDevice>> = _pairedHosts.asStateFlow()

    // Pre-allocated static buffers to avoid GC allocations during high-speed gaming
    private val staticReportBuffer = ByteArray(9)
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatchScope = CoroutineScope(Dispatchers.IO)
    private var inputLoopJob: Job? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "Bluetooth HID Device profile connected")
                hidDevice = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "Bluetooth HID Device profile disconnected")
                hidDevice = null
                isAppRegistered = false
                _status.value = ConnectionStatus.WaitingForHost
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=${pluggedDevice?.address}")
            isAppRegistered = registered
            if (registered) {
                if (connectedHostDevice != null) {
                    val name = getDeviceNameSafe(connectedHostDevice)
                    _status.value = ConnectionStatus.Connected(name, connectedHostDevice?.address ?: "")
                } else {
                    _status.value = ConnectionStatus.WaitingForHost
                }
                refreshPairedHosts()
            } else {
                _status.value = ConnectionStatus.Error("HID App registration failed")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device?.address}, state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHostDevice = device
                    val name = getDeviceNameSafe(device)
                    _status.value = ConnectionStatus.Connected(name, device?.address ?: "")
                    // Send neutral calibration report immediately upon connection
                    sendReportImmediate()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHostDevice == device || connectedHostDevice == null) {
                        connectedHostDevice = null
                        _status.value = ConnectionStatus.WaitingForHost
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _status.value = ConnectionStatus.Registering
                }
            }
            refreshPairedHosts()
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (id == REPORT_ID_GAMEPAD) {
                gamepadState.packReport(staticReportBuffer)
                if (hasBluetoothConnectPermission()) {
                    try {
                        hidDevice?.replyReport(device, type, id, staticReportBuffer)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException on onGetReport", e)
                    }
                }
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // Acknowledge report from host
            if (hasBluetoothConnectPermission()) {
                try {
                    hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS.toByte())
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException on onSetReport", e)
                }
            }
        }
    }

    fun initialize() {
        if (bluetoothAdapter == null) {
            _status.value = ConnectionStatus.BluetoothDisabled
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _status.value = ConnectionStatus.BluetoothDisabled
            return
        }

        _status.value = ConnectionStatus.Registering
        try {
            val success = bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
            if (!success) {
                _status.value = ConnectionStatus.HidNotSupported
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring HID profile proxy", e)
            _status.value = ConnectionStatus.HidNotSupported
        }

        startInputLoop()
        refreshPairedHosts()
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val hid = hidDevice ?: return
        if (!hasBluetoothConnectPermission()) {
            _status.value = ConnectionStatus.Error("Izin Bluetooth Connect diperlukan")
            return
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "DualSense Wireless Controller",
            "PS5 Virtual Bluetooth Gamepad",
            "Sony Interactive Entertainment",
            0x08.toByte(), // Gamepad subclass
            HID_REPORT_DESCRIPTOR
        )

        try {
            hid.registerApp(sdp, null, null, executor, hidCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during registerApp", e)
            _status.value = ConnectionStatus.Error("Izin ditolak saat mendaftarkan HID App")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HID app", e)
            _status.value = ConnectionStatus.Error("Gagal mendaftarkan HID App: ${e.message}")
        }
    }

    /**
     * High-speed non-blocking input loop sending reports at ~100Hz whenever state is dirty.
     */
    private fun startInputLoop() {
        inputLoopJob?.cancel()
        inputLoopJob = dispatchScope.launch {
            while (isActive) {
                if (isAppRegistered && connectedHostDevice != null) {
                    if (gamepadState.isDirty.get()) {
                        sendReportImmediate()
                    }
                }
                delay(DISPATCH_INTERVAL_MS)
            }
        }
    }

    /**
     * Serializes and dispatches the current gamepad state to the connected host immediately.
     */
    @SuppressLint("MissingPermission")
    fun sendReportImmediate() {
        val device = connectedHostDevice ?: return
        val hid = hidDevice ?: return
        if (!hasBluetoothConnectPermission()) return

        synchronized(staticReportBuffer) {
            gamepadState.packReport(staticReportBuffer)
            try {
                hid.sendReport(device, REPORT_ID_GAMEPAD.toInt(), staticReportBuffer)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException sending report", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending HID report", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHost(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) return
        try {
            hidDevice?.connect(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to host ${device.address}", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectHost() {
        val device = connectedHostDevice ?: return
        if (!hasBluetoothConnectPermission()) return
        try {
            hidDevice?.disconnect(device)
            connectedHostDevice = null
            _status.value = ConnectionStatus.WaitingForHost
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect host", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedHosts() {
        if (!hasBluetoothConnectPermission() || bluetoothAdapter == null) {
            _pairedHosts.value = emptyList()
            return
        }
        try {
            val bonded = bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
            _pairedHosts.value = bonded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bonded devices", e)
            _pairedHosts.value = emptyList()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceNameSafe(device: BluetoothDevice?): String {
        if (device == null) return "Perangkat Host"
        return if (hasBluetoothConnectPermission()) {
            device.name ?: device.address ?: "Host PC"
        } else {
            device.address ?: "Host PC"
        }
    }

    @SuppressLint("MissingPermission")
    fun release() {
        inputLoopJob?.cancel()
        try {
            if (isAppRegistered && hasBluetoothConnectPermission()) {
                hidDevice?.unregisterApp()
            }
            if (hidDevice != null && bluetoothAdapter != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up HID manager", e)
        }
        hidDevice = null
        connectedHostDevice = null
        isAppRegistered = false
    }
}
