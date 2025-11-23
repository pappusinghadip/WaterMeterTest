package com.example.watermetertest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.watermetertest.adapters.ActionsPagerAdapter
import com.example.watermetertest.databinding.ActivityMainBinding
import com.example.watermetertest.models.PermissionsResponse
import com.example.watermetertest.presentation.viewmodels.BluetoothViewModel
import com.example.watermetertest.presentation.viewmodels.MainViewModel
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private lateinit var pagerAdapter: ActionsPagerAdapter
    private val mainViewModel: MainViewModel by viewModels()
    private val bluetoothViewModel: BluetoothViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if user is logged in
        if (!mainViewModel.isLoggedIn()) {
            navigateToLogin()
            return
        }

        setupToolbar()
        setupNavigationDrawer()
        setupViewPager()
        observeViewModel()
        setupPermissionsManager()
        setupBluetoothConnection()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupViewPager() {
        pagerAdapter = ActionsPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()
    }

    private fun observeViewModel() {
        mainViewModel.getPermissions().observe(this) { permissions ->
            onPermissionsLoaded(permissions)
        }
        mainViewModel.getPermissionsError().observe(this) { error ->
            onPermissionsError(error)
        }
        mainViewModel.getPermissionsLoading().observe(this) { loading ->
            binding.progressBar.visibility = if (loading == true) View.VISIBLE else View.GONE
        }
        mainViewModel.getSessionExpired().observe(this) { expired ->
            if (expired == true) {
                onSessionExpired()
            }
        }
    }

    private fun setupPermissionsManager() {
        mainViewModel.startPermissionsRefresh()
    }

    private fun setupBluetoothConnection() {
        // Load paired devices and check for saved connection
        bluetoothViewModel.loadPairedDevices()

        // Try to auto-connect to previously selected device if not already connected
        val selectedAddress = bluetoothViewModel.getSelectedDeviceAddress()
        if (selectedAddress != null && bluetoothViewModel.getIsConnected().value != true) {
            // Find the device in paired devices and attempt connection
            bluetoothViewModel.getPairedDevices().observe(this) { pairedDevices ->
                // Remove observer immediately to prevent repeated triggers
                bluetoothViewModel.getPairedDevices().removeObservers(this)
                
                pairedDevices?.let { devices ->
                    devices.find { it.address == selectedAddress }?.let { device ->
                        Log.d("MainActivity", "Auto-connecting to ${device.getDisplayName()}")
                        bluetoothViewModel.connectToDevice(device)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            R.id.action_bluetooth_settings -> {
                val intent = Intent(this, BluetoothSettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh connection state when returning from other activities
        setupBluetoothConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel will handle cleanup automatically
    }

    // ViewModel observer methods
    private fun onPermissionsLoaded(permissions: PermissionsResponse?) {
        if (permissions?.actions != null) {
            pagerAdapter.setActions(permissions.actions)
        }
    }

    private fun onPermissionsError(error: String?) {
        error?.let {
            Log.d("Error", it)
            Toast.makeText(this, "Error loading permissions: $it", Toast.LENGTH_LONG).show()
        }
    }

    private fun onSessionExpired() {
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
        navigateToLogin()
    }

    private fun logout() {
        mainViewModel.logout()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_bluetooth -> {
                val intent = Intent(this, BluetoothSettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_commands -> {
                // Already on commands screen, just close drawer
            }
            R.id.nav_common -> {
                // Switch to Common tab
                switchToTab("Common")
            }
            R.id.nav_network -> {
                // Switch to Network tab
                switchToTab("Network")
            }
            R.id.nav_logout -> {
                logout()
            }
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun switchToTab(tabName: String) {
        for (i in 0 until pagerAdapter.itemCount) {
            if (tabName == pagerAdapter.getPageTitle(i)) {
                binding.viewPager.currentItem = i
                break
            }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}