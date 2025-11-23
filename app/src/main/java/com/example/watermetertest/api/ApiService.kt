package com.example.watermetertest.api

import com.example.watermetertest.models.LoginResponse
import com.example.watermetertest.models.PermissionsResponse
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @FormUrlEncoded
    @POST("login")
    fun login(
        @Field("u") username: String,
        @Field("p") password: String,
        @Field("lat") latitude: String,
        @Field("lng") longitude: String
    ): Call<LoginResponse>

    @GET("waterPermissions")
    fun getPermissions(@Header("Token") token: String): Call<PermissionsResponse>
}