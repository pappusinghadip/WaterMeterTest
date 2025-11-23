package com.example.watermetertest

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import com.example.watermetertest.adapters.BluetoothDevicesAdapter
import com.example.watermetertest.models.BluetoothDeviceModel
import com.example.watermetertest.presentation.viewmodels.BluetoothViewModel
import com.google.android.material.button.MaterialButton

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BluetoothSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2
    }

    private lateinit var toolbar: Toolbar
    private lateinit var currentDeviceText: TextView
    private lateinit var disconnectButton: MaterialButton
    private lateinit var scanButton: MaterialButton
    private lateinit var systemSettingsButton: MaterialButton
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var progressBar: View

    private lateinit var adapter: BluetoothDevicesAdapter
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private var connectionProgressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_settings)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        checkBluetoothPermissions()
        
        // Attempt to reconnect to previously connected device
        attemptAutoReconnect()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        currentDeviceText = findViewById(R.id.currentDeviceText)
        disconnectButton = findViewById(R.id.disconnectButton)
        scanButton = findViewById(R.id.scanButton)
        systemSettingsButton = findViewById(R.id.systemSettingsButton)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        adapter = BluetoothDevicesAdapter(object : BluetoothDevicesAdapter.OnDeviceClickListener {
            override fun onDeviceClick(device: BluetoothDeviceModel) {
                val connectedDevice = bluetoothViewModel.getConnectedDeviceSync()
                if (bluetoothViewModel.isDeviceConnected() && connectedDevice?.address == device.address) {
                    Toast.makeText(this@BluetoothSettingsActivity, "Already connected to ${device.getDisplayName()}", Toast.LENGTH_SHORT).show()
                    return
                }
                
                showConnectionProgress("Connecting to ${device.getDisplayName()}...")
                bluetoothViewModel.connectToDevice(device)
            }

            override fun onDeviceLongClick(device: BluetoothDeviceModel) {
                showUnpairDialog(device)
            }
        })
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener { startBluetoothScan() }
        disconnectButton.setOnClickListener { bluetoothViewModel.disconnect() }
        systemSettingsButton.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        bluetoothViewModel.getDiscoveredDevices().observe(this) { devices ->
            updateDiscoveredDevices(devices)
        }
        bluetoothViewModel.getPairedDevices().observe(this) { devices ->
            updatePairedDevices(devices)
        }
        bluetoothViewModel.getConnectedDevice().observe(this) { device ->
            if (device != null) {
                dismissConnectionProgress()
            }
            updateCurrentDevice(device)
        }
        bluetoothViewModel.getIsScanning().observe(this) { isScanning ->
            updateScanningState(isScanning)
        }
        bluetoothViewModel.getIsConnected().observe(this) { isConnected ->
            if (isConnected == true) {
                dismissConnectionProgress()
            }
            updateConnectionState(isConnected)
        }
        bluetoothViewModel.getError().observe(this) { error ->
            dismissConnectionProgress()
            showError(error)
        }
    }

    private fun updateDiscoveredDevices(devices: List<BluetoothDeviceModel>?) {
        devices?.let {
            adapter.setDevices(it)
            updateEmptyState()
        }
    }

    private fun updatePairedDevices(devices: List<BluetoothDeviceModel>?) {
        devices?.let {
            adapter.setDevices(it)

            val selectedAddress = bluetoothViewModel.getSelectedDeviceAddress()
            selectedAddress?.let { address ->
                adapter.setSelectedDevice(address)
            }

            updateEmptyState()
        }
    }

    private fun updateCurrentDevice(device: BluetoothDeviceModel?) {
        if (device != null) {
            currentDeviceText.text = "🔗 CONNECTED\n${device.getDisplayName()}\n${device.address}"
            disconnectButton.visibility = View.VISIBLE
            device.address?.let { adapter.setSelectedDevice(it) }
        } else {
            val selectedAddress = bluetoothViewModel.getSelectedDeviceAddress()
            if (selectedAddress != null) {
                currentDeviceText.text = "📱 PAIRED (Not Connected)\n$selectedAddress"
                adapter.setSelectedDevice(selectedAddress)
            } else {
                currentDeviceText.text = "❌ No device selected\nTap a device to connect"
                adapter.setSelectedDevice(null)
            }
            disconnectButton.visibility = View.GONE
        }
    }

    private fun updateScanningState(isScanning: Boolean?) {
        if (isScanning == true) {
            scanButton.text = "Stop Scan"
            progressBar.visibility = View.VISIBLE
        } else {
            scanButton.text = "Scan"
            progressBar.visibility = View.GONE
        }
    }

    private fun updateConnectionState(isConnected: Boolean?) {
        // Handled in updateCurrentDevice
    }

    private fun showError(error: String?) {
        error?.let {
            if (it.startsWith("Pairing initiated")) {
                showConnectionProgress("Pairing... Please wait")
                // Add timeout for pairing dialog (15 seconds)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (connectionProgressDialog?.isShowing == true) {
                        dismissConnectionProgress()
                        Toast.makeText(this, "Pairing timed out or failed", Toast.LENGTH_LONG).show()
                    }
                }, 15000)
            } else {
                dismissConnectionProgress()
                Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Pre-Android 12 permissions
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            checkBluetoothEnabled()
        }
    }

    private fun checkBluetoothEnabled() {
        if (!bluetoothViewModel.isBluetoothAvailable()) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothViewModel.isBluetoothEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            loadInitialData()
        }
    }

    private fun loadInitialData() {
        bluetoothViewModel.loadPairedDevices()
    }

    private fun startBluetoothScan() {
        Log.d("BluetoothSettings", "startBluetoothScan called")
        val isScanning = bluetoothViewModel.getIsScanning().value
        if (isScanning == true) {
            bluetoothViewModel.stopDiscovery()
            return
        }

        if (!bluetoothViewModel.isBluetoothEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothViewModel.startDiscovery()
    }

    private fun updateEmptyState() {
        val hasDevices = adapter.itemCount > 0
        devicesRecyclerView.visibility = if (hasDevices) View.VISIBLE else View.GONE
        emptyStateText.visibility = if (hasDevices) View.GONE else View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allPermissionsGranted) {
                checkBluetoothEnabled()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
                loadInitialData()
            } else {
                Toast.makeText(this, "Bluetooth must be enabled to use this feature", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUnpairDialog(device: BluetoothDeviceModel) {
        android.util.Log.d("BluetoothSettings", "showUnpairDialog called for: ${device.getDisplayName()}")
        
        if (!device.isPaired) {
            Toast.makeText(this, "Device is not paired", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Unpair Device")
            .setMessage("Do you want to unpair ${device.getDisplayName()}?")
            .setPositiveButton("Unpair") { _, _ ->
                android.util.Log.d("BluetoothSettings", "Unpairing device: ${device.address}")
                bluetoothViewModel.unpairDevice(device)
                // Optimistic update is handled in repository/manager
                // System broadcast will trigger final update

            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showConnectionProgress(message: String) {
        dismissConnectionProgress()
        connectionProgressDialog = AlertDialog.Builder(this)
            .setTitle("Connecting")
            .setMessage(message)
            .setCancelable(false)
            .create()
        connectionProgressDialog?.show()
    }
    
    
    private fun dismissConnectionProgress() {
        connectionProgressDialog?.dismiss()
        connectionProgressDialog = null
    }
    
    /**
     * Attempts to reconnect to the previously connected device
     * This is called when the activity opens to restore the connection
     * Uses observeOnce to prevent repeated reconnection attempts
     */
    private fun attemptAutoReconnect() {
        // Check if already connected using synchronous check (source of truth)
        if (bluetoothViewModel.isDeviceConnected()) {
            Log.d("BluetoothSettings", "Already connected, skipping auto-reconnect")
            return
        }
        
        // Get the previously selected device address
        val selectedAddress = bluetoothViewModel.getSelectedDeviceAddress()
        if (selectedAddress == null) {
            Log.d("BluetoothSettings", "No previously selected device, skipping auto-reconnect")
            return
        }
        
        // Find the device in paired devices list - observe ONCE to prevent loop
        bluetoothViewModel.getPairedDevices().observe(this) { pairedDevices ->
            // Remove observer immediately to prevent repeated triggers
            bluetoothViewModel.getPairedDevices().removeObservers(this)
            
            val deviceToConnect = pairedDevices?.find { it.address == selectedAddress }
            if (deviceToConnect != null) {
                Log.d("BluetoothSettings", "Auto-reconnecting to: ${deviceToConnect.getDisplayName()}")
                
                // Show connection progress
                showConnectionProgress("Reconnecting to ${deviceToConnect.getDisplayName()}...")
                
                // Attempt connection
                bluetoothViewModel.connectToDevice(deviceToConnect)
            } else {
                Log.d("BluetoothSettings", "Previously selected device not found in paired devices")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dismissConnectionProgress()
    }
}
