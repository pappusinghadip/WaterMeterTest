package com.example.watermetertest.managers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.watermetertest.models.BluetoothDeviceModel
import com.example.watermetertest.utils.SharedPrefsManager
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    interface BluetoothScanListener {
        fun onDeviceFound(device: BluetoothDeviceModel)
        fun onScanStarted()
        fun onScanFinished()
        fun onError(error: String)
        fun onDevicePaired(device: BluetoothDeviceModel)
    }
    
    interface BluetoothConnectionListener {
        fun onConnected(device: BluetoothDeviceModel)
        fun onDisconnected()
        fun onConnectionFailed(error: String)
        fun onDataSent()
        fun onDataSendFailed(error: String)
        fun onPairingInitiated(message: String)
    }
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null
    private val sharedPrefsManager: SharedPrefsManager = SharedPrefsManager(context)
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    
    var scanListener: BluetoothScanListener? = null
    var connectionListener: BluetoothConnectionListener? = null
    
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && checkBluetoothPermission()) {
                        val deviceName = device.name
                        val deviceAddress = device.address
                        val isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                        
                        val deviceModel = BluetoothDeviceModel(deviceName, deviceAddress, isPaired)
                        scanListener?.onDeviceFound(deviceModel)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Receiver: ACTION_DISCOVERY_STARTED")
                    discoveryStartedEventReceived = true
                    scanListener?.onScanStarted()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Receiver: ACTION_DISCOVERY_FINISHED")
                    scanListener?.onScanFinished()
                    
                    if (pendingDiscovery) {
                        Log.d(TAG, "Executing pending discovery")
                        pendingDiscovery = false
                        startDiscovery()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && checkBluetoothPermission()) {
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        Log.d(TAG, "Bond state changed: ${device.address} -> $bondState")
                        
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            Log.d(TAG, "Device paired successfully")
                            val deviceModel = BluetoothDeviceModel(device.name, device.address, true)
                            scanListener?.onDevicePaired(deviceModel)
                            
                            Log.d(TAG, "Device paired. Waiting 2.5s before connecting...")
                            mainHandler.postDelayed({
                                if (!isConnected()) {
                                    Log.d(TAG, "Pairing delay complete, attempting connection...")
                                    connectToDevice(deviceModel)
                                }
                            }, 2500)
                        } else if (bondState == BluetoothDevice.BOND_NONE) {
                            Log.d(TAG, "Device unpaired: ${device.address}")
                            val unpairedDevice = BluetoothDeviceModel(device.name, device.address, false)
                            scanListener?.onDevicePaired(unpairedDevice)
                        }
                    }
                }
            }
        }
    }
    
    init {
        Log.d(TAG, "BluetoothManager initialized: $this")
        registerBluetoothReceiver()
    }
    
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }
    
    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null
    
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    private fun checkBluetoothPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getPairedDevices(): List<BluetoothDeviceModel> {
        val pairedDevices = mutableListOf<BluetoothDeviceModel>()
        
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            return pairedDevices
        }
        
        try {
            val bondedDevices = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                val deviceName = device.name
                val deviceAddress = device.address
                pairedDevices.add(BluetoothDeviceModel(deviceName, deviceAddress, true))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting paired devices", e)
            scanListener?.onError("Permission denied for accessing paired devices")
        }
        
        return pairedDevices
    }
    
    private var pendingDiscovery = false
    private var discoveryStartedEventReceived = false

    fun startDiscovery(): Boolean {
        Log.d(TAG, "startDiscovery called")
        if (!checkBluetoothPermission()) {
            Log.e(TAG, "startDiscovery failed: Permissions missing")
            scanListener?.onError("Bluetooth permissions missing")
            return false
        }
        if (bluetoothAdapter == null) {
            Log.e(TAG, "startDiscovery failed: BluetoothAdapter is null")
            scanListener?.onError("Bluetooth not available")
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "startDiscovery failed: Bluetooth not enabled")
            scanListener?.onError("Bluetooth not enabled")
            return false
        }
        
        try {
            if (bluetoothAdapter.isDiscovering) {
                Log.d(TAG, "Cancelling existing discovery and queuing new one")
                bluetoothAdapter.cancelDiscovery()
                pendingDiscovery = true
                return true // We accepted the request and will execute it when ready
            }
            
            discoveryStartedEventReceived = false
            val result = bluetoothAdapter.startDiscovery()
            Log.d(TAG, "startDiscovery result: $result")
            
            if (result) {
                // Optimistic update: Update UI immediately since broadcast can be delayed
                scanListener?.onScanStarted()
                
                // Watchdog: Android's Bluetooth stack sometimes accepts startDiscovery() (returns true)
                // but fails to actually broadcast ACTION_DISCOVERY_STARTED.
                // We check after 1s if the broadcast was received.
                mainHandler.postDelayed({
                    if (!discoveryStartedEventReceived) {
                        Log.w(TAG, "Watchdog: Scan accepted but no broadcast received. Receiver might be stale.")
                        
                        // Attempt to recover by re-registering the receiver
                        try {
                            context.unregisterReceiver(bluetoothReceiver)
                        } catch (e: Exception) {
                            // Receiver might not have been registered
                        }
                        registerBluetoothReceiver()
                        
                        // Retry scan with fresh receiver
                        bluetoothAdapter.startDiscovery()
                    }
                }, 1000)
                
                // Safety Timeout: Prevent UI from getting stuck in "Scanning" state
                // if ACTION_DISCOVERY_FINISHED is missed.
                mainHandler.postDelayed({
                    if (isScanning()) {
                        Log.d(TAG, "Scan timeout reached, forcing finish")
                        scanListener?.onScanFinished()
                    }
                }, 15000)
            } else {
                Log.e(TAG, "Failed to start discovery. State: ${bluetoothAdapter.state}")
            }
            
            return result
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting discovery", e)
            scanListener?.onError("Permission denied for device discovery")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting discovery", e)
            return false
        }
    }
    
    fun stopDiscovery() {
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            return
        }
        
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping discovery", e)
        }
    }
    
    fun connectToDevice(deviceModel: BluetoothDeviceModel) {
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            connectionListener?.onConnectionFailed("Bluetooth not available")
            return
        }
        
        val device = bluetoothAdapter.getRemoteDevice(deviceModel.address)
        if (device == null) {
            mainHandler.post {
                connectionListener?.onConnectionFailed("Device not found")
            }
            return
        }

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "Device not paired, initiating pairing only: ${device.address}")
            device.createBond()
            mainHandler.post {
                connectionListener?.onPairingInitiated("Pairing initiated. Please wait...")
            }
            return
        }
        
        Thread {
            var attempt = 1
            val maxAttempts = 2
            var success = false
            
            while (attempt <= maxAttempts && !success) {
                try {
                    Log.d(TAG, "Connection attempt $attempt of $maxAttempts")
                    
                    stopDiscovery()
                    Thread.sleep(1000)
                    
                    try {
                        Log.d(TAG, "Attempting service discovery...")
                        device.fetchUuidsWithSdp()
                        Thread.sleep(2000)
                    } catch (sdpException: Exception) {
                        Log.w(TAG, "SDP failed, continuing anyway: ${sdpException.message}")
                    }
                    
                    // Check if already connected to the same device
                    if (connectedDevice?.address == deviceModel.address && isConnected()) {
                        Log.d(TAG, "Already connected to ${deviceModel.address}")
                        mainHandler.post {
                            connectionListener?.onConnected(deviceModel)
                        }
                        return@Thread
                    }

                    // Ensure previous connection is closed
                    cleanup()
                    
                    Log.d(TAG, "Attempting to connect to device: ${deviceModel.address} (${deviceModel.name})")
                    
                    var connected = false
                    var lastException: IOException? = null
                    
                    // Method 1: Secure RFCOMM
                    try {
                        Log.d(TAG, "Trying method 1: Secure RFCOMM")
                        bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        bluetoothSocket?.connect()
                        connected = true
                        Log.d(TAG, "Method 1 succeeded")
                    } catch (e: IOException) {
                        Log.w(TAG, "Method 1 failed: ${e.message}")
                        lastException = e
                        try { bluetoothSocket?.close() } catch (ignore: Exception) {}
                    }
                    
                    // Method 2: Insecure RFCOMM (New Fallback)
                    if (!connected) {
                        try {
                            Log.d(TAG, "Trying method 2: Insecure RFCOMM")
                            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                            bluetoothSocket?.connect()
                            connected = true
                            Log.d(TAG, "Method 2 succeeded")
                        } catch (e: IOException) {
                            Log.w(TAG, "Method 2 failed: ${e.message}")
                            lastException = e
                            try { bluetoothSocket?.close() } catch (ignore: Exception) {}
                        }
                    }
                    
                    // Method 3: Reflection (Channel 1)
                    if (!connected) {
                        try {
                            Log.d(TAG, "Trying method 3: Reflection channel 1")
                            bluetoothSocket = device.javaClass
                                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                                .invoke(device, 1) as BluetoothSocket
                            bluetoothSocket?.connect()
                            connected = true
                            Log.d(TAG, "Method 3 succeeded")
                        } catch (e: Exception) {
                            Log.w(TAG, "Method 3 failed: ${e.message}")
                            lastException = IOException(e)
                            try { bluetoothSocket?.close() } catch (ignore: Exception) {}
                        }
                    }
                    
                    // Method 4: Reflection (Channels 2-3 only to save time)
                    if (!connected) {
                        for (channel in 2..3) {
                            if (connected) break
                            try {
                                Log.d(TAG, "Trying method 4: Reflection channel $channel")
                                bluetoothSocket = device.javaClass
                                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                                    .invoke(device, channel) as BluetoothSocket
                                bluetoothSocket?.connect()
                                connected = true
                                Log.d(TAG, "Method 4 succeeded with channel $channel")
                                break
                            } catch (e: Exception) {
                                Log.w(TAG, "Method 4 channel $channel failed: ${e.message}")
                                lastException = IOException(e)
                                try { bluetoothSocket?.close() } catch (ignore: Exception) {}
                            }
                        }
                    }
                    
                    if (!connected) {
                        throw IOException("All connection methods failed. Last error: ${lastException?.message ?: "Unknown"}")
                    }
                    
                    outputStream = bluetoothSocket?.outputStream
                    connectedDevice = device
                    
                    if (bluetoothSocket?.isConnected != true || outputStream == null) {
                        throw IOException("Connection established but socket or stream is null")
                    }
                    
                    sharedPrefsManager.saveSelectedBluetoothDevice(deviceModel.address!!)
                    
                    Log.d(TAG, "Successfully connected to ${deviceModel.address}")
                    success = true
                    
                    mainHandler.post {
                        connectionListener?.onConnected(deviceModel)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Connection attempt $attempt failed", e)
                    if (attempt == maxAttempts) {
                        mainHandler.post {
                            connectionListener?.onConnectionFailed("Connection failed: ${e.message}")
                        }
                    } else {
                        // Wait before retry
                        try { Thread.sleep(1500) } catch (ignore: Exception) {}
                    }
                }
                attempt++
            }
        }.start()
    }
    
    fun disconnect() {
        Thread {
            try {
                outputStream?.close()
                outputStream = null
                bluetoothSocket?.close()
                bluetoothSocket = null
                connectedDevice = null
                
                mainHandler.post {
                    connectionListener?.onDisconnected()
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Error during disconnection", e)
            }
        }.start()
    }
    
    fun isScanning(): Boolean {
        return bluetoothAdapter?.isDiscovering == true
    }

    fun isConnected(): Boolean {
        val socketConnected = bluetoothSocket?.isConnected == true
        val streamAvailable = outputStream != null
        val deviceSet = connectedDevice != null
        return socketConnected && streamAvailable && deviceSet
    }
    
    fun getConnectedDevice(): BluetoothDeviceModel? {
        if (connectedDevice != null && checkBluetoothPermission()) {
            try {
                return BluetoothDeviceModel(
                    connectedDevice?.name,
                    connectedDevice?.address,
                    true
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception getting connected device", e)
            }
        }
        return null
    }
    
    fun sendData(hexPayload: String) {
        Log.d(TAG, "sendData called with payload: $hexPayload")
        Log.d(TAG, "sendData caller: ${Log.getStackTraceString(Exception())}")
        if (!isConnected()) {
            Log.e(TAG, "sendData failed: Not connected")
            connectionListener?.onDataSendFailed("Not connected to any device")
            return
        }
        
        Thread {
            try {
                if (bluetoothSocket == null || bluetoothSocket?.isConnected != true || outputStream == null) {
                    mainHandler.post {
                        connectionListener?.onDataSendFailed("Connection lost. Please reconnect.")
                        connectionListener?.onDisconnected()
                    }
                    return@Thread
                }
                
                val data = hexStringToByteArray(hexPayload)
                Log.d(TAG, "Sending ${data.size} bytes: $hexPayload")
                
                outputStream?.write(data)
                outputStream?.flush()
                
                Log.d(TAG, "Successfully sent data: $hexPayload")
                
                mainHandler.post {
                    connectionListener?.onDataSent()
                }
                
            } catch (e: IOException) {
                Log.e(TAG, "Error sending data: ${e.message}", e)
                
                try {
                    outputStream?.close()
                    outputStream = null
                    bluetoothSocket?.close()
                    bluetoothSocket = null
                    connectedDevice = null
                } catch (closeException: IOException) {
                    Log.e(TAG, "Error closing broken connection", closeException)
                }
                
                mainHandler.post {
                    connectionListener?.onDataSendFailed(
                        "Failed to send data: ${e.message}. Please reconnect the device."
                    )
                    connectionListener?.onDisconnected()
                }
            }
        }.start()
    }
    
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val cleanHex = hexString.replace(Regex("[^0-9A-Fa-f]"), "")
        
        val length = cleanHex.length
        val data = ByteArray(length / 2)
        
        for (i in 0 until length step 2) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
        }
        
        return data
    }
    
    fun getSelectedDeviceAddress(): String? {
        return sharedPrefsManager.getSelectedBluetoothDevice()
    }
    
    fun unpairDevice(deviceModel: BluetoothDeviceModel): Boolean {
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            connectionListener?.onConnectionFailed("Bluetooth not available")
            return false
        }
        
        try {
            // Ensure discovery is cancelled before unpairing
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }

            val device = bluetoothAdapter.getRemoteDevice(deviceModel.address)
            
            if (connectedDevice != null && connectedDevice?.address == device.address) {
                disconnect()
            }
            
            val savedAddress = sharedPrefsManager.getSelectedBluetoothDevice()
            if (savedAddress == device.address) {
                sharedPrefsManager.clearSelectedBluetoothDevice()
            }
            
            device.javaClass.getMethod("removeBond").invoke(device)
            Log.d(TAG, "Unpaired device: ${device.address}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error unpairing device", e)
            connectionListener?.onConnectionFailed("Failed to unpair device: ${e.message}")
            return false
        }
    }
    
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        
        stopDiscovery()
        disconnect()
    }
}
