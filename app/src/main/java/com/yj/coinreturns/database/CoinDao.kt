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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(coin:Coin)

    @Query("DELETE FROM Coin WHERE SYMBOL LIKE :symbol")
    fun delete(symbol: String)

    @Update
    fun update(coin:Coin)
}