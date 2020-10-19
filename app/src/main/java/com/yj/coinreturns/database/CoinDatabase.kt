package com.mvvm.mybinance.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Coin::class],version = 1)
abstract class CoinDatabase : RoomDatabase() {
    abstract fun coinDao(): CoinDao

    companion object {
        @Volatile
        private var INSTANCE: CoinDatabase? = null

        fun getInstance(context: Context): CoinDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                CoinDatabase::class.java, "Coin.db"
            ).build()
    }
}