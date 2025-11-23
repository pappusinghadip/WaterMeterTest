package com.example.watermetertest.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.example.watermetertest.managers.BluetoothManager
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
    BluetoothManager.BluetoothConnectionListener {

    private val bluetoothManager = BluetoothManager(context)

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
        loadPairedDevices()
    }

    /**
     * Get discovered devices as LiveData
     * Properly converts StateFlow to LiveData for reactive updates
     */
    fun getDiscoveredDevices(): LiveData<List<BluetoothDeviceModel>> {
        return _discoveredDevices.asLiveData()
    }

    /**
     * Get paired devices as LiveData
     * Properly converts StateFlow to LiveData for reactive updates
     */
    fun getPairedDevices(): LiveData<List<BluetoothDeviceModel>> {
        return _pairedDevices.asLiveData()
    }

    /**
     * Get connected device as LiveData
     * Properly converts StateFlow to LiveData for reactive updates
     * Returns nullable LiveData since device can be null when not connected
     */
    fun getConnectedDevice(): LiveData<BluetoothDeviceModel?> {
        return _connectedDevice.asLiveData()
    }

    /**
     * Get scanning state as LiveData
     * Properly converts StateFlow to LiveData for reactive updates
     */
    fun getIsScanning(): LiveData<Boolean> {
        return _isScanning.asLiveData()
    }

    /**
     * Get connection state as LiveData
     * Properly converts StateFlow to LiveData for reactive updates
     */
    fun getIsConnected(): LiveData<Boolean> {
        return _isConnected.asLiveData()
    }


    private val _errorLiveData = MutableLiveData<String>()
    fun getError(): LiveData<String> = _errorLiveData

    private val _dataSentLiveData = MutableLiveData<Boolean>()
    fun getDataSent(): LiveData<Boolean> = _dataSentLiveData


    fun startDiscovery() {
        _discoveredDevices.value = emptyList()
        bluetoothManager.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothManager.stopDiscovery()
    }

    fun connectToDevice(device: BluetoothDeviceModel) {
        bluetoothManager.connectToDevice(device)
    }

    fun disconnect() {
        bluetoothManager.disconnect()
    }

    fun sendData(hexPayload: String) {
        bluetoothManager.sendData(hexPayload)
    }

    /**
     * Load paired devices from BluetoothManager
     * Updates the StateFlow which triggers LiveData observers
     */
    fun loadPairedDevices() {
        _pairedDevices.value = bluetoothManager.getPairedDevices()
    }
    
    fun getSelectedDeviceAddress(): String? {
        return bluetoothManager.getSelectedDeviceAddress()
    }
    
    /**
     * Unpairs the device and updates the local list immediately for responsiveness.
     */
    fun unpairDevice(device: BluetoothDeviceModel) {
        // Optimistic update: Remove from UI immediately while background work proceeds
        val currentList = _pairedDevices.value.toMutableList()
        currentList.removeAll { it.address == device.address }
        _pairedDevices.value = currentList

        bluetoothManager.unpairDevice(device)
    }

    fun isBluetoothAvailable(): Boolean = bluetoothManager.isBluetoothAvailable()
    fun isBluetoothEnabled(): Boolean = bluetoothManager.isBluetoothEnabled()
    fun isDeviceConnected(): Boolean = bluetoothManager.isConnected()
    fun getConnectedDeviceSync(): BluetoothDeviceModel? = bluetoothManager.getConnectedDevice()

    fun cleanup() {
        bluetoothManager.cleanup()
    }

    // BluetoothScanListener implementation
    override fun onDeviceFound(device: BluetoothDeviceModel) {
        val currentList = _discoveredDevices.value.toMutableList()
        if (currentList.none { it.address == device.address }) {
            currentList.add(device)
            _discoveredDevices.value = currentList
        }
    }

    override fun onScanStarted() {
        _isScanning.value = true
    }

    override fun onScanFinished() {
        _isScanning.value = false
    }

    override fun onError(error: String) {
        _errorLiveData.postValue(error)
    }

    override fun onDevicePaired(device: BluetoothDeviceModel) {
        loadPairedDevices()
        
        // Sync discovered devices list with new bond state
        val currentDiscovered = _discoveredDevices.value.toMutableList()
        val index = currentDiscovered.indexOfFirst { it.address == device.address }
        
        if (index != -1) {
            // Update existing device with new state
            currentDiscovered[index] = device
            _discoveredDevices.value = currentDiscovered
        } else if (!device.isPaired) {
            // Device was unpaired, add back to available list
            currentDiscovered.add(device)
            _discoveredDevices.value = currentDiscovered
        }
    }

    // BluetoothConnectionListener implementation
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
        // Optional: expose pairing status
    }
}
