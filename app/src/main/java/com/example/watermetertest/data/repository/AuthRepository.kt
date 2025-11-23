package com.example.watermetertest.data.repository

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.watermetertest.api.ApiClient
import com.example.watermetertest.models.LoginResponse
import com.example.watermetertest.utils.SharedPrefsManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuthRepository(private val context: Context) {
    
    private val sharedPrefsManager = SharedPrefsManager(context)
    private val loginResult = MutableLiveData<String>()
    private val loginState = MutableLiveData<Boolean>()

    init {
        // Initialize login state
        loginState.value = sharedPrefsManager.isLoggedIn()
    }

    fun getLoginResult(): LiveData<String> = loginResult

    fun getLoginState(): LiveData<Boolean> = loginState

    fun login(username: String, password: String) {
        val location = getLastKnownLocation()
        val latitude = location?.latitude?.toString() ?: "0.0"
        val longitude = location?.longitude?.toString() ?: "0.0"

        ApiClient.getApiService().login(username, password, latitude, longitude)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val token = response.body()?.token
                        if (!token.isNullOrEmpty()) {
                            sharedPrefsManager.saveToken(token)
                            loginState.value = true
                            loginResult.value = "success"
                        } else {
                            loginResult.value = "Invalid response from server"
                        }
                    } else {
                        loginResult.value = "Login failed. Please check your credentials."
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.d("NetworkError", t.message ?: "Unknown error")
                    loginResult.value = "Network error: ${t.message}"
                }
            })
    }

    fun logout() {
        sharedPrefsManager.logout()
        loginState.value = false
    }

    fun isLoggedIn(): Boolean = sharedPrefsManager.isLoggedIn()

    fun getToken(): String? = sharedPrefsManager.getToken()

    private fun getLastKnownLocation(): Location? {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.let {
                var location = it.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location == null) {
                    location = it.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                return location
            }
        } catch (e: SecurityException) {
            Log.e("AuthRepository", "Location permission not granted", e)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error getting location", e)
        }
        return null
    }
}