package com.yj.coinreturns.repository

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import com.mvvm.mybinance.model.Coin
import com.mvvm.mybinance.model.CoinDao
import com.mvvm.mybinance.model.CoinDatabase

class CoinRepository(application: Application, exchange: String) {

    private var mCoinDatabase: CoinDatabase
    private var mCoinDao: CoinDao
    private var mCoinList: LiveData<MutableList<Coin>>

    init {
        mCoinDatabase = CoinDatabase.getInstance(application)
        mCoinDao = mCoinDatabase.coinDao()
        mCoinList = mCoinDao.getAllFromRoom(exchange)
    }

    fun getAllFromRoom() = mCoinList

    fun deleteAll() {
        mCoinDao.deleteAll()
    }

    fun insert(coin: Coin) {
        Log.d("fhrm", "CoinRepository -insert(),    coin: ${coin.id}")
        mCoinDao.insert(coin)
    }

    fun delete(symbol: String) {
        mCoinDao.delete(symbol)
    }

    fun update(coin: Coin) {
        mCoinDao.update(coin)
    }

}