package com.example.watermetertest.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.watermetertest.api.ApiClient
import com.example.watermetertest.models.PermissionsResponse
import com.example.watermetertest.utils.SharedPrefsManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class PermissionsRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionsRepository"
        private const val REFRESH_INTERVAL = 3 * 60 * 60 * 1000L // 3 hours
    }

    private val sharedPrefsManager = SharedPrefsManager(context)
    private var refreshTimer: Timer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissions = MutableLiveData<PermissionsResponse>()
    private val error = MutableLiveData<String>()
    private val loading = MutableLiveData<Boolean>()
    private val sessionExpired = MutableLiveData<Boolean>()

    init {
        loading.value = false
        sessionExpired.value = false
    }

    fun getPermissions(): LiveData<PermissionsResponse> = permissions

    fun getError(): LiveData<String> = error

    fun getLoading(): LiveData<Boolean> = loading

    fun getSessionExpired(): LiveData<Boolean> = sessionExpired

    fun startPeriodicRefresh() {
        Log.d(TAG, "Starting periodic permissions refresh every ${REFRESH_INTERVAL / 1000} seconds")

        // Always fetch permissions immediately when starting
        fetchPermissions()

        // Start periodic refresh timer
        refreshTimer?.cancel()

        refreshTimer = Timer("PermissionsRefreshTimer").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (sharedPrefsManager.isLoggedIn()) {
                        Log.d(TAG, "Periodic permissions refresh triggered")
                        fetchPermissions()
                    } else {
                        Log.d(TAG, "User not logged in, skipping permissions refresh")
                    }
                }
            }, REFRESH_INTERVAL, REFRESH_INTERVAL)
        }
    }

    fun stopPeriodicRefresh() {
        Log.d(TAG, "Stopping periodic permissions refresh")
        refreshTimer?.cancel()
        refreshTimer = null
    }

    fun fetchPermissions() {
        val token = sharedPrefsManager.getToken()
        if (token == null) {
            mainHandler.post { sessionExpired.value = true }
            return
        }

        // Check network connectivity first
        if (!isNetworkAvailable()) {
            mainHandler.post { error.value = "No internet connection available" }
            return
        }

        Log.d("API_DEBUG", "Fetching permissions with token: ${token.take(20)}...")
        mainHandler.post { loading.value = true }
        
        ApiClient.getApiService().getPermissions(token).enqueue(object : Callback<PermissionsResponse> {
            override fun onResponse(call: Call<PermissionsResponse>, response: Response<PermissionsResponse>) {
                mainHandler.post {
                    loading.value = false
                    when {
                        response.isSuccessful && response.body() != null -> {
                            Log.d(TAG, "Permissions refreshed successfully")
                            sharedPrefsManager.saveLastPermissionsFetch(System.currentTimeMillis())
                            permissions.value = response.body()
                            error.value = null
                        }
                        response.code() == 401 || response.code() == 403 -> {
                            Log.w(TAG, "Session expired (HTTP ${response.code()}), logging out user")
                            sharedPrefsManager.logout()
                            sessionExpired.value = true
                        }
                        else -> {
                            Log.e(TAG, "Failed to load permissions: HTTP ${response.code()}")
                            error.value = "Failed to load permissions: ${response.code()}"
                        }
                    }
                }
            }

            override fun onFailure(call: Call<PermissionsResponse>, t: Throwable) {
                mainHandler.post {
                    loading.value = false
                    Log.e(TAG, "Network error fetching permissions", t)

                    val errorMessage = when {
                        t.message?.contains("Unable to resolve host") == true ->
                            "Cannot connect to server. Check your internet connection."
                        else -> "Network error: ${t.message}"
                    }
                    error.value = errorMessage
                }
            }
        })
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.let {
                val activeNetworkInfo: NetworkInfo? = it.activeNetworkInfo
                return activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
        }
        return false
    }
}