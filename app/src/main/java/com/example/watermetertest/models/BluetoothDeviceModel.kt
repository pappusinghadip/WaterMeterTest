package com.example.watermetertest.models

import java.io.Serializable

data class BluetoothDeviceModel(
    var name: String? = null,
    var address: String? = null,
    var isPaired: Boolean = false,
    var isSelected: Boolean = false,
    var deviceType: DeviceType = DeviceType.CLASSIC
) : Serializable {
    
    enum class DeviceType {
        CLASSIC,
        BLE
    }
    
    constructor(name: String?, address: String?, isPaired: Boolean) : this(name, address, isPaired, false, DeviceType.CLASSIC)
    constructor(name: String?, address: String?, isPaired: Boolean, type: DeviceType) : this(name, address, isPaired, false, type)
    
    fun getDisplayName(): String {
        return if (!name.isNullOrEmpty()) name!! else "Unknown Device"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as BluetoothDeviceModel
        return address == that.address
    }
    
    override fun hashCode(): Int {
        return address?.hashCode() ?: 0
    }
}
