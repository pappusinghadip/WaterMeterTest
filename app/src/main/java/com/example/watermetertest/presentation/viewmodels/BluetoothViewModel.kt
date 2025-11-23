package com.example.watermetertest.presentation.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.watermetertest.data.repository.BluetoothRepository
import com.example.watermetertest.models.BluetoothDeviceModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {

    fun getDiscoveredDevices(): LiveData<List<BluetoothDeviceModel>> =
        bluetoothRepository.getDiscoveredDevices()

    fun getPairedDevices(): LiveData<List<BluetoothDeviceModel>> =
        bluetoothRepository.getPairedDevices()

    fun getConnectedDevice(): LiveData<BluetoothDeviceModel?> =
        bluetoothRepository.getConnectedDevice()

    fun getIsScanning(): LiveData<Boolean> =
        bluetoothRepository.getIsScanning()

    fun getIsConnected(): LiveData<Boolean> =
        bluetoothRepository.getIsConnected()

    fun getError(): LiveData<String> =
        bluetoothRepository.getError()

    fun getDataSent(): LiveData<Boolean> =
        bluetoothRepository.getDataSent()

    fun isBluetoothAvailable(): Boolean =
        bluetoothRepository.isBluetoothAvailable()

    fun isBluetoothEnabled(): Boolean =
        bluetoothRepository.isBluetoothEnabled()

    fun isDeviceConnected(): Boolean =
        bluetoothRepository.isDeviceConnected()

    fun getConnectedDeviceSync(): BluetoothDeviceModel? =
        bluetoothRepository.getConnectedDeviceSync()

    fun loadPairedDevices() {
        bluetoothRepository.loadPairedDevices()
    }

    fun startDiscovery() {
        bluetoothRepository.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothRepository.stopDiscovery()
    }

    fun connectToDevice(device: BluetoothDeviceModel) {
        bluetoothRepository.connectToDevice(device)
    }

    fun disconnect() {
        bluetoothRepository.disconnect()
    }

    fun sendData(hexPayload: String) {
        bluetoothRepository.sendData(hexPayload)
    }

    fun getSelectedDeviceAddress(): String? =
        bluetoothRepository.getSelectedDeviceAddress()

    fun unpairDevice(device: BluetoothDeviceModel) {
        bluetoothRepository.unpairDevice(device)
    }

    override fun onCleared() {
        super.onCleared()
        // Do not cleanup repository here as it is a Singleton and needs to survive ViewModel lifecycle
        // bluetoothRepository.cleanup()
    }
}
