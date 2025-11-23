package com.example.watermetertest

import android.app.Application
import com.example.watermetertest.api.ApiClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WaterMeterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("API_DEBUG", "Application onCreate - Initializing ApiClient")
        ApiClient.init(this)
    }
}
