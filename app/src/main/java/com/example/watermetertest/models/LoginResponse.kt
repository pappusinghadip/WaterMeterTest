package com.example.watermetertest.models

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("Token")
    var token: String? = null,
    
    @SerializedName("TrackingToken")
    var trackingToken: String? = null,
    
    var type: String? = null,
    var block: Boolean = false,
    var msg: String? = null,
    var startTimePause: String? = null,
    var endTimePause: String? = null,
    var userid: String? = null
)