package com.code4you.buche

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class BucaReport(val latitude: Double, val longitude: Double)

interface BucaApiService {
    @POST("buca")
    fun reportBuca(@Body bucaReport: BucaReport): Call<Void>
}
