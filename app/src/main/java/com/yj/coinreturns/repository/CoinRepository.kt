package com.yj.coinreturns.repository

import android.app.Application
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
        mCoinDao.insert(coin)
    }

    fun delete(coin: Coin) {
        mCoinDao.delete(coin)
    }

    fun update(coin: Coin) {
        mCoinDao.update(coin)
    }

}