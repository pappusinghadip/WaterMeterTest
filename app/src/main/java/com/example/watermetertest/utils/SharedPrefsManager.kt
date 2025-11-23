package com.example.watermetertest.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "WaterMeterPrefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LAST_PERMISSIONS_FETCH = "last_permissions_fetch"
        private const val KEY_SELECTED_BLUETOOTH_DEVICE = "selected_bluetooth_device"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }
    
    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }
    
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }
    
    fun logout() {
        sharedPreferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_LAST_PERMISSIONS_FETCH)
            .apply()
    }
    
    fun saveLastPermissionsFetch(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_PERMISSIONS_FETCH, timestamp).apply()
    }
    
    fun getLastPermissionsFetch(): Long {
        return sharedPreferences.getLong(KEY_LAST_PERMISSIONS_FETCH, 0)
    }
    
    fun shouldRefreshPermissions(): Boolean {
        val lastFetch = getLastPermissionsFetch()
        val currentTime = System.currentTimeMillis()
        val threeHours = 3 * 60 * 60 * 1000L // 3 hours in milliseconds
        return (currentTime - lastFetch) > threeHours
    }
    
    fun saveSelectedBluetoothDevice(address: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_BLUETOOTH_DEVICE, address).apply()
    }

    fun getSelectedBluetoothDevice(): String? {
        return sharedPreferences.getString(KEY_SELECTED_BLUETOOTH_DEVICE, null)
    }
    
    fun clearSelectedBluetoothDevice() {
        sharedPreferences.edit().remove(KEY_SELECTED_BLUETOOTH_DEVICE).apply()
    }
}