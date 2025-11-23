package com.example.watermetertest.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.watermetertest.R
import com.example.watermetertest.models.BluetoothDeviceModel

class BluetoothDevicesAdapter(private val listener: OnDeviceClickListener) :
    RecyclerView.Adapter<BluetoothDevicesAdapter.DeviceViewHolder>() {

    interface OnDeviceClickListener {
        fun onDeviceClick(device: BluetoothDeviceModel)
        fun onDeviceLongClick(device: BluetoothDeviceModel)
    }

    private var devices: List<BluetoothDeviceModel> = ArrayList()
    private var selectedDeviceAddress: String? = null

    fun setDevices(devices: List<BluetoothDeviceModel>?) {
        this.devices = devices ?: ArrayList()
        notifyDataSetChanged()
    }

    fun setSelectedDevice(address: String?) {
        selectedDeviceAddress = address
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        private val deviceAddress: TextView = itemView.findViewById(R.id.deviceAddress)
        private val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
        private val selectedIndicator: ImageView = itemView.findViewById(R.id.selectedIndicator)

        fun bind(device: BluetoothDeviceModel) {
            deviceName.text = device.name ?: "Unknown Device"
            deviceAddress.text = device.address

            if (device.isPaired) {
                deviceStatus.visibility = View.VISIBLE
                deviceStatus.text = "Paired"
            } else {
                deviceStatus.visibility = View.GONE
            }

            if (device.address == selectedDeviceAddress) {
                selectedIndicator.visibility = View.VISIBLE
            } else {
                selectedIndicator.visibility = View.GONE
            }

            itemView.setOnClickListener {
                listener.onDeviceClick(device)
            }

            itemView.setOnLongClickListener {
                listener.onDeviceLongClick(device)
                true
            }
        }
    }
}
