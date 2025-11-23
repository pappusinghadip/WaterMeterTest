package com.example.watermetertest.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.example.watermetertest.managers.BluetoothManager
import com.example.watermetertest.managers.BleManager
import com.example.watermetertest.models.BluetoothDeviceModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothRepository @Inject constructor(context: Context) : 
    BluetoothManager.BluetoothScanListener, 
    BluetoothManager.BluetoothConnectionListener,
    BleManager.BleScanListener,
    BleManager.BleConnectionListener {

    private val bluetoothManager = BluetoothManager(context)
    private val bleManager = BleManager(context)

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = _discoveredDevices

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceModel>> = _pairedDevices

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceModel?> = _connectedDevice

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val _dataSent = MutableSharedFlow<Boolean>()
    val dataSent: SharedFlow<Boolean> = _dataSent

    init {
        android.util.Log.d("BluetoothRepository", "BluetoothRepository initialized: $this")
        bluetoothManager.scanListener = this
        bluetoothManager.connectionListener = this
        bleManager.scanListener = this
        bleManager.connectionListener = this
        loadPairedDevices()
    }

    // ... (LiveData getters remain same) ...
    fun getDiscoveredDevices(): LiveData<List<BluetoothDeviceModel>> = _discoveredDevices.asLiveData()
    fun getPairedDevices(): LiveData<List<BluetoothDeviceModel>> = _pairedDevices.asLiveData()
    fun getConnectedDevice(): LiveData<BluetoothDeviceModel?> = _connectedDevice.asLiveData()
    fun getIsScanning(): LiveData<Boolean> = _isScanning.asLiveData()
    fun getIsConnected(): LiveData<Boolean> = _isConnected.asLiveData()
    
    private val _errorLiveData = MutableLiveData<String>()
    fun getError(): LiveData<String> = _errorLiveData

    private val _dataSentLiveData = MutableLiveData<Boolean>()
    fun getDataSent(): LiveData<Boolean> = _dataSentLiveData


    fun startDiscovery() {
        _discoveredDevices.value = emptyList()
        // Start both scans
        bluetoothManager.startDiscovery()
        bleManager.startScan()
    }

    fun stopDiscovery() {
        bluetoothManager.stopDiscovery()
        bleManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDeviceModel) {
        if (device.deviceType == BluetoothDeviceModel.DeviceType.BLE) {
            bleManager.connect(device.address!!)
        } else {
            bluetoothManager.connectToDevice(device)
        }
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        bleManager.disconnect()
    }

    fun sendData(hexPayload: String) {
        val device = _connectedDevice.value
        if (device?.deviceType == BluetoothDeviceModel.DeviceType.BLE) {
            bleManager.sendData(hexPayload)
        } else {
            bluetoothManager.sendData(hexPayload)
        }
    }

    fun loadPairedDevices() {
        _pairedDevices.value = bluetoothManager.getPairedDevices()
    }
    
    fun getSelectedDeviceAddress(): String? {
        return bluetoothManager.getSelectedDeviceAddress()
    }
    
    fun unpairDevice(device: BluetoothDeviceModel) {
        if (device.deviceType == BluetoothDeviceModel.DeviceType.BLE) {
            // BLE devices usually don't need explicit unpairing in app logic unless bonded
            // For now, just remove from list if needed, but BLE scan is dynamic
        } else {
            val currentList = _pairedDevices.value.toMutableList()
            currentList.removeAll { it.address == device.address }
            _pairedDevices.value = currentList
            bluetoothManager.unpairDevice(device)
        }
    }

    fun isBluetoothAvailable(): Boolean = bluetoothManager.isBluetoothAvailable()
    fun isBluetoothEnabled(): Boolean = bluetoothManager.isBluetoothEnabled()
    
    fun isDeviceConnected(): Boolean {
        // Check both managers
        return bluetoothManager.isConnected() || (_connectedDevice.value?.deviceType == BluetoothDeviceModel.DeviceType.BLE && _isConnected.value)
    }
    
    fun getConnectedDeviceSync(): BluetoothDeviceModel? {
        if (bluetoothManager.isConnected()) {
            return bluetoothManager.getConnectedDevice()
        }
        // For BLE, we rely on our local state since BleManager doesn't persist connection state as strictly as Classic socket
        if (_isConnected.value && _connectedDevice.value?.deviceType == BluetoothDeviceModel.DeviceType.BLE) {
            return _connectedDevice.value
        }
        return null
    }

    fun cleanup() {
        bluetoothManager.cleanup()
        bleManager.close()
    }

    // BluetoothScanListener implementation (Classic)
    override fun onDeviceFound(device: BluetoothDeviceModel) {
        addDiscoveredDevice(device)
    }

    override fun onScanStarted() {
        _isScanning.value = true
    }

    override fun onScanFinished() {
        // Only set to false if both are finished? 
        // For simplicity, if Classic finishes, we might still be scanning BLE.
        // But usually we want to stop spinner if Classic stops.
        // Let's keep it simple: if Classic stops, we consider scan stopped for UI purposes
        // or we could track both. For now, Classic is the driver.
        _isScanning.value = false
    }

    override fun onError(error: String) {
        _errorLiveData.postValue(error)
    }

    override fun onDevicePaired(device: BluetoothDeviceModel) {
        loadPairedDevices()
        updateDiscoveredDevice(device)
    }

    // BleScanListener implementation
    override fun onBleDeviceFound(device: BluetoothDeviceModel) {
        addDiscoveredDevice(device)
    }

    override fun onBleScanStarted() {
        _isScanning.value = true
    }

    override fun onBleScanFinished() {
        // If Classic is also done, then we are done.
        if (!bluetoothManager.isScanning()) {
            _isScanning.value = false
        }
    }

    override fun onBleError(error: String) {
        // Log but maybe don't show toast for every BLE error if Classic is working
        android.util.Log.e("BluetoothRepository", "BLE Error: $error")
    }

    // BluetoothConnectionListener implementation (Classic)
    override fun onConnected(device: BluetoothDeviceModel) {
        _isConnected.value = true
        _connectedDevice.value = device
    }

    override fun onDisconnected() {
        _isConnected.value = false
        _connectedDevice.value = null
    }

    override fun onConnectionFailed(error: String) {
        _isConnected.value = false
        _connectedDevice.value = null
        _errorLiveData.postValue(error)
    }

    override fun onDataSent() {
        _dataSentLiveData.postValue(true)
    }

    override fun onDataSendFailed(error: String) {
        _errorLiveData.postValue(error)
        _dataSentLiveData.postValue(false)
    }

    override fun onPairingInitiated(message: String) {
        // Optional
    }

    // BleConnectionListener implementation
    override fun onBleConnected(device: BluetoothDeviceModel) {
        _isConnected.value = true
        _connectedDevice.value = device
    }

    override fun onBleDisconnected() {
        _isConnected.value = false
        _connectedDevice.value = null
    }

    override fun onBleConnectionFailed(error: String) {
        _isConnected.value = false
        _connectedDevice.value = null
        _errorLiveData.postValue(error)
    }

    override fun onBleDataSent() {
        _dataSentLiveData.postValue(true)
    }

    override fun onBleDataSendFailed(error: String) {
        _errorLiveData.postValue(error)
        _dataSentLiveData.postValue(false)
    }

    override fun onBleDataReceived(data: String) {
        // Handle received data if needed (e.g. expose via another flow)
        // For now, maybe just log or toast?
        // The app architecture seems to focus on sending commands.
        // If we need to show responses, we should add a `dataReceived` flow.
    }

    // Helper methods
    private fun addDiscoveredDevice(device: BluetoothDeviceModel) {
        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == device.address }
        
        if (existingIndex == -1) {
            currentList.add(device)
            _discoveredDevices.value = currentList
        } else {
            // Device already exists. Check if we should update it.
            val existingDevice = currentList[existingIndex]
            
            // If we found it via BLE and it was previously CLASSIC, update to BLE
            // This ensures we try BLE connection for dual-mode devices which often don't support SPP
            if (existingDevice.deviceType == BluetoothDeviceModel.DeviceType.CLASSIC && 
                device.deviceType == BluetoothDeviceModel.DeviceType.BLE) {
                
                // Preserve name if the new one is empty/unknown
                val newName = if (!device.name.isNullOrEmpty()) device.name else existingDevice.name
                
                val updatedDevice = device.copy(name = newName)
                currentList[existingIndex] = updatedDevice
                _discoveredDevices.value = currentList
            }
        }
    }

    private fun updateDiscoveredDevice(device: BluetoothDeviceModel) {
        val currentDiscovered = _discoveredDevices.value.toMutableList()
        val index = currentDiscovered.indexOfFirst { it.address == device.address }
        if (index != -1) {
            currentDiscovered[index] = device
            _discoveredDevices.value = currentDiscovered
        }
    }
}
