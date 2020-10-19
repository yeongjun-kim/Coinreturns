package com.cluttered.cryptocurrency.services

import com.cluttered.cryptocurrency.BinanceConstants.ENDPOINT_SECURITY_TYPE_SIGNED_HEADER
import com.cluttered.cryptocurrency.model.withdraw.*
import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

interface WithdrawService {

    companion object {
        val ONE_MINUTE_IN_MILLIS = TimeUnit.MINUTES.toMillis(1)
    }

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @POST("wapi/v3/withdraw.html")
    fun withdraw(
            @Query("asset") asset: String,
            @Query("address") address: String,
            @Query("addressTag") addressTag: String? = null,
            @Query("amount") amount: BigDecimal,
            @Query("name") name: String? = null,
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<List<WithdrawRequest>>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/depositHistory.html")
    fun depositHistory(
            @Query("asset") asset: String? = null,
            @Query("status") status: DepositStatus? = null,
            @Query("startTime") startTime: Long? = null,
            @Query("endTime") endTime: Long? = null,
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<DepositHistory>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/withdrawHistory.html")
    fun withdrawHistory(
            @Query("asset") asset: String? = null,
            @Query("status") status: WithdrawStatus? = null,
            @Query("startTime") startTime: Long? = null,
            @Query("endTime") endTime: Long? = null,
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<WithdrawHistory>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/depositAddress.html")
    fun depositAddress(
            @Query("asset") asset: String? = null,
            @Query("status") status: Boolean? = null,
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<List<DepositAddress>>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/withdrawFee.html")
    fun withdrawFee(
            @Query("asset") asset: String? = null,
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<List<WithdrawFee>>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/accountStatus.html")
    fun accountStatus(
            @Query("recvWindow") recvWindow: Long = ONE_MINUTE_IN_MILLIS,
            @Query("timestamp") timestamp: Long)
            : Observable<AccountStatus>

    @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
    @GET("wapi/v3/systemStatus.html")
    fun systemStatus() : Observable<SystemStatus>
}