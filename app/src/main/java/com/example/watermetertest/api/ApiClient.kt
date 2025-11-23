package com.example.watermetertest.api

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

object ApiClient {
    private const val BASE_URL = "http://apm.integraaposta.com/gestione/api/"
    private var retrofit: Retrofit? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d("API_DEBUG", "ApiClient initialized with context: ${appContext != null}")
    }

    fun getClient(): Retrofit {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // Custom logging interceptor to see exact request
            val debugInterceptor = Interceptor { chain ->
                val request = chain.request()
                Log.d("API_DEBUG", "Request URL: ${request.url}")
                Log.d("API_DEBUG", "Request Method: ${request.method}")
                Log.d("API_DEBUG", "Request Headers:")
                for (i in 0 until request.headers.size) {
                    Log.d("API_DEBUG", "  ${request.headers.name(i)}: ${request.headers.value(i)}")
                }
                if (request.body != null) {
                    Log.d("API_DEBUG", "Request Body Type: ${request.body?.contentType()}")
                    try {
                        val buffer = Buffer()
                        request.body?.writeTo(buffer)
                        Log.d("API_DEBUG", "Request Body: ${buffer.readUtf8()}")
                    } catch (e: Exception) {
                        Log.d("API_DEBUG", "Could not read request body: ${e.message}")
                    }
                }

                val response = chain.proceed(request)
                Log.d("API_DEBUG", "Response Code: ${response.code}")

                // Log response body for all responses to debug the issue
                try {
                    val responseBody = response.peekBody(2048).string()
                    Log.d("API_DEBUG", "Response Body: $responseBody")
                    Log.d("API_DEBUG", "Response Content-Type: ${response.header("Content-Type")}")
                } catch (e: Exception) {
                    Log.d("API_DEBUG", "Could not read response body: ${e.message}")
                }

                response
            }

            // Add headers interceptor
            val headerInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()

                val userAgent = buildUserAgent()
                val deviceIp = getDeviceIp()
                val timestamp = getCurrentTimestamp()
                val location = getLastKnownLocation()

                Log.d("API_DEBUG", "Generated User-Agent: $userAgent")
                Log.d("API_DEBUG", "Generated IP: $deviceIp")
                Log.d("API_DEBUG", "Generated Timestamp: $timestamp")
                if (location != null) {
                    Log.d("API_DEBUG", "Generated Location: ${location.latitude},${location.longitude}")
                } else {
                    Log.d("API_DEBUG", "No location available")
                }

                val requestBuilder = originalRequest.newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .addHeader("X-Forwarded-For", deviceIp)
                    .addHeader("X-Version-AppCheck", timestamp)
                    .addHeader("Host", "apm.integraaposta.com")
                    .addHeader("Connection", "keep-alive")

                if (location != null) {
                    requestBuilder.addHeader("X-GPS-Latitude", location.latitude.toString())
                    requestBuilder.addHeader("X-GPS-Longitude", location.longitude.toString())
                }

                val newRequest = requestBuilder.build()
                chain.proceed(newRequest)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .addInterceptor(debugInterceptor)
                .addInterceptor(logging)
                .build()

            val gson = GsonBuilder()
                .setLenient()
                .create()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        return retrofit!!
    }

    fun getApiService(): ApiService {
        return getClient().create(ApiService::class.java)
    }

    private fun buildUserAgent(): String {
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        return String.format(
            "IntegraaAppV.24.08.01[Icn];Version-Operating-System:%s;Brand-Model:%s;USER_ID:No value",
            androidVersion, deviceModel
        )
    }

    private fun getDeviceIp(): String {
        appContext?.let { context ->
            try {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiManager?.let {
                    val wifiInfo: WifiInfo = it.connectionInfo
                    val ip = wifiInfo.ipAddress

                    // Convert IP address from int to readable format
                    return String.format(
                        Locale.getDefault(), "%d.%d.%d.%d",
                        (ip and 0xff),
                        (ip shr 8 and 0xff),
                        (ip shr 16 and 0xff),
                        (ip shr 24 and 0xff)
                    )
                }
            } catch (e: Exception) {
                Log.e("ApiClient", "Error getting device IP", e)
            }
        }
        return "192.168.1.1" // Fallback IP
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getLastKnownLocation(): Location? {
        appContext?.let { context ->
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                locationManager?.let {
                    // Try GPS first
                    var location = it.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (location == null) {
                        // Fallback to network location
                        location = it.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }
                    return location
                }
            } catch (e: SecurityException) {
                Log.e("ApiClient", "Location permission not granted", e)
            } catch (e: Exception) {
                Log.e("ApiClient", "Error getting location", e)
            }
        }
        return null
    }
}