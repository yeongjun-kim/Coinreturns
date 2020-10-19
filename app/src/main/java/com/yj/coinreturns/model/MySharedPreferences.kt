package com.yj.coinreturns.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Build.ID
import android.provider.ContactsContract.DisplayNameSources.NICKNAME
import android.provider.Telephony.Carriers.PASSWORD


private const val FILENAME = "prefs"
private const val PREF_TEXT = "Text"

class MySharedPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILENAME, 0)

    val SECRET_COINBASE: String = "SECRET_COINBASE"
    val API_COINBASE: String = "API_COINBASE"
    val SECRET_HUOBI: String = "SECRET_HUOBI"
    val API_HUOBI: String = "API_HUOBI"
    val SECERET_BINANCE: String = "SECERET_BINANCE"
    var API_BINANCE: String = "API_BINANCE"
    var LAST_CHECK_TIMESTAMP_BINANCE: Long = 0


    var secretCoinbase: String?
        get() = prefs.getString(SECRET_COINBASE, null)
        set(value) = prefs.edit().putString(SECRET_COINBASE, value).apply()
    var apiCoinBase: String?
        get() = prefs.getString(API_COINBASE, null)
        set(value) = prefs.edit().putString(API_COINBASE, value).apply()
    var secretHuobi: String?
        get() = prefs.getString(SECRET_HUOBI, null)
        set(value) = prefs.edit().putString(SECRET_HUOBI, value).apply()
    var apiHuobi: String?
        get() = prefs.getString(API_HUOBI, null)
        set(value) = prefs.edit().putString(API_HUOBI, value).apply()

    var secretBinance: String?
        get() = prefs.getString(SECERET_BINANCE, null)
        set(value) = prefs.edit().putString(SECERET_BINANCE, value).apply()

    var apiBinance: String?
        get() = prefs.getString(API_BINANCE, null)
        set(value) = prefs.edit().putString(API_BINANCE, value).apply()

    var lastCheckTimeStampBinance: Long
        get() = prefs.getLong("LAST_CHECK_TIMESTAMP_BINANCE", 0)
        set(value) = prefs.edit().putLong("LAST_CHECK_TIMESTAMP_BINANCE", value).apply()
}