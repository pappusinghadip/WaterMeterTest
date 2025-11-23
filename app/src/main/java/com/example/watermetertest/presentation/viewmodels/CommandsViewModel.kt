package com.example.watermetertest.presentation.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.watermetertest.data.repository.BluetoothRepository
import com.example.watermetertest.models.Action
import com.example.watermetertest.models.BluetoothDeviceModel
import com.example.watermetertest.models.Command
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommandsViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : ViewModel() {
    
    private val currentAction = MutableLiveData<Action>()
    private val commandResult = MutableLiveData<String>()

    fun getCurrentAction(): LiveData<Action> = currentAction

    fun getCommandResult(): LiveData<String> = commandResult

    fun getConnectedDevice(): LiveData<BluetoothDeviceModel?> =
        bluetoothRepository.getConnectedDevice()

    fun getIsConnected(): LiveData<Boolean> =
        bluetoothRepository.getIsConnected()

    fun getBluetoothError(): LiveData<String> =
        bluetoothRepository.getError()

    fun getDataSent(): LiveData<Boolean> =
        bluetoothRepository.getDataSent()

    fun setAction(action: Action) {
        currentAction.value = action
    }

    fun executeCommand(command: Command, payload: String?) {
        android.util.Log.d("CommandsViewModel", "executeCommand called: ${command.label}, payload: $payload")
        
        when {
            !bluetoothRepository.isBluetoothAvailable() || !bluetoothRepository.isBluetoothEnabled() -> {
                commandResult.value = "Bluetooth not available or enabled"
                return
            }
            payload.isNullOrBlank() -> {
                commandResult.value = "Invalid payload"
                return
            }
        }
        
        // Check if connected, if not try to reconnect
        if (bluetoothRepository.getIsConnected().value != true) {
            android.util.Log.w("CommandsViewModel", "Not connected, attempting reconnection...")
            val selectedAddress = bluetoothRepository.getSelectedDeviceAddress()
            
            if (selectedAddress != null) {
                commandResult.value = "Reconnecting to device..."
                
                // Use viewModelScope for safe coroutine execution
                viewModelScope.launch {
                    // Get paired devices
                    bluetoothRepository.loadPairedDevices()
                    delay(500) // Wait for load
                    
                    val pairedDevices = bluetoothRepository.getPairedDevices().value
                    val deviceToConnect = pairedDevices?.find { it.address == selectedAddress }
                    
                    if (deviceToConnect != null) {
                        android.util.Log.d("CommandsViewModel", "Reconnecting to ${deviceToConnect.address}")
                        bluetoothRepository.connectToDevice(deviceToConnect)
                        
                        // Wait for connection
                        delay(3000)
                        
                        if (bluetoothRepository.getIsConnected().value == true) {
                            android.util.Log.d("CommandsViewModel", "Reconnection successful, sending command")
                            commandResult.value = "Sending command: ${command.label}"
                            bluetoothRepository.sendData(payload)
                        } else {
                            commandResult.value = "Reconnection failed. Please connect manually."
                        }
                    } else {
                        commandResult.value = "Device not found. Please connect manually."
                    }
                }
            } else {
                commandResult.value = "No Bluetooth device connected"
            }
            return
        }

        android.util.Log.d("CommandsViewModel", "All checks passed, sending command")
        commandResult.value = "Sending command: ${command.label}"
        bluetoothRepository.sendData(payload)
    }
}
