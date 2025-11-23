package com.example.watermetertest.managers

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.watermetertest.models.BluetoothDeviceModel
import java.util.*

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        // Generic UUIDs - in a real app these might be specific
        // For now we will discover services dynamically
    }

    interface BleScanListener {
        fun onBleDeviceFound(device: BluetoothDeviceModel)
        fun onBleScanStarted()
        fun onBleScanFinished()
        fun onBleError(error: String)
    }

    interface BleConnectionListener {
        fun onBleConnected(device: BluetoothDeviceModel)
        fun onBleDisconnected()
        fun onBleConnectionFailed(error: String)
        fun onBleDataSent()
        fun onBleDataSendFailed(error: String)
        fun onBleDataReceived(data: String)
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var writableCharacteristic: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var scanListener: BleScanListener? = null
    var connectionListener: BleConnectionListener? = null

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device != null && checkBluetoothPermission()) {
                // Prioritize name from ScanRecord (Advertisement Data) as it's often more up-to-date
                val scanRecordName = result.scanRecord?.deviceName
                val deviceName = if (!scanRecordName.isNullOrEmpty()) scanRecordName else device.name

                // Only show devices with names to reduce clutter
                if (!deviceName.isNullOrEmpty()) {
                    val deviceModel = BluetoothDeviceModel(
                        deviceName, 
                        device.address, 
                        false, // BLE devices usually don't need classic pairing
                        BluetoothDeviceModel.DeviceType.BLE
                    )
                    scanListener?.onBleDeviceFound(deviceModel)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error: $errorCode")
            scanListener?.onBleError("BLE Scan failed: $errorCode")
            isScanning = false
            scanListener?.onBleScanFinished()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.")
                    // Discover services after successful connection
                    if (checkBluetoothPermission()) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.")
                    mainHandler.post {
                        connectionListener?.onBleDisconnected()
                    }
                    close()
                }
            } else {
                Log.w(TAG, "GATT error: $status")
                mainHandler.post {
                    connectionListener?.onBleConnectionFailed("GATT Error: $status")
                }
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered.")
                findWritableCharacteristic(gatt)
                
                // Notify connection success
                val device = gatt.device
                val deviceModel = BluetoothDeviceModel(
                    device.name, 
                    device.address, 
                    false, 
                    BluetoothDeviceModel.DeviceType.BLE
                )
                
                mainHandler.post {
                    connectionListener?.onBleConnected(deviceModel)
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
                mainHandler.post {
                    connectionListener?.onBleDataSent()
                }
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
                mainHandler.post {
                    connectionListener?.onBleDataSendFailed("Write failed: $status")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Handle received data
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val hexString = byteArrayToHexString(data)
                Log.d(TAG, "Received data: $hexString")
                mainHandler.post {
                    connectionListener?.onBleDataReceived(hexString)
                }
            }
        }
    }

    private fun findWritableCharacteristic(gatt: BluetoothGatt) {
        // Iterate through services and characteristics to find one we can write to
        // This is a generic approach since we don't know the specific UUIDs
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val properties = characteristic.properties
                if ((properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                    writableCharacteristic = characteristic
                    Log.d(TAG, "Found writable characteristic: ${characteristic.uuid}")
                    
                    // Also try to enable notifications on this or other characteristics if needed
                    // For now, we just focus on finding a write target
                    return
                }
            }
        }
        Log.w(TAG, "No writable characteristic found!")
    }

    fun startScan() {
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            scanListener?.onBleError("Permissions missing or Bluetooth unavailable")
            return
        }

        if (isScanning) return

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                scanListener?.onBleError("BLE Scanner unavailable")
                return
            }

            isScanning = true
            scanListener?.onBleScanStarted()
            scanner.startScan(scanCallback)

            // Stop scan after 10 seconds
            mainHandler.postDelayed({
                stopScan()
            }, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            scanListener?.onBleError("Error starting scan: ${e.message}")
            isScanning = false
        }
    }

    fun stopScan() {
        if (!checkBluetoothPermission() || bluetoothAdapter == null || !isScanning) return

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            scanner?.stopScan(scanCallback)
            isScanning = false
            scanListener?.onBleScanFinished()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
        }
    }

    fun connect(address: String) {
        if (!checkBluetoothPermission() || bluetoothAdapter == null) {
            connectionListener?.onBleConnectionFailed("Bluetooth unavailable")
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(address)
            // AutoConnect = false for faster initial connection
            // Use TRANSPORT_LE to force BLE connection, critical for dual-mode devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to BLE device", e)
            connectionListener?.onBleConnectionFailed("Connection error: ${e.message}")
        }
    }

    fun disconnect() {
        if (!checkBluetoothPermission() || bluetoothGatt == null) return
        bluetoothGatt?.disconnect()
    }

    fun close() {
        if (!checkBluetoothPermission()) return
        bluetoothGatt?.close()
        bluetoothGatt = null
        writableCharacteristic = null
    }

    fun sendData(hexString: String) {
        if (!checkBluetoothPermission() || bluetoothGatt == null || writableCharacteristic == null) {
            connectionListener?.onBleDataSendFailed("Not connected or no writable characteristic")
            return
        }

        try {
            val characteristic = writableCharacteristic!!
            characteristic.value = hexStringToByteArray(hexString)
            // Set write type based on properties
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            if (!success) {
                connectionListener?.onBleDataSendFailed("Write initiation failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing characteristic", e)
            connectionListener?.onBleDataSendFailed("Write error: ${e.message}")
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
