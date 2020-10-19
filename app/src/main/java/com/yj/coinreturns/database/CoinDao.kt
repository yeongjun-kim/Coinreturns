package com.mvvm.mybinance.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*

@Dao
interface CoinDao {

    @Query("SELECT * FROM Coin WHERE exchange LIKE :exchange ORDER BY symbol")
    fun getAllFromRoom(exchange:String):LiveData<MutableList<Coin>>

    @Query("DELETE FROM Coin")
    fun deleteAll()

    @Insert
    fun insert(coin:Coin)

    @Delete
    fun delete(coin:Coin)

    @Update
    fun update(coin:Coin)
}