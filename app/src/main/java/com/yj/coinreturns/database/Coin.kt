package com.mvvm.mybinance.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Coin")
data class Coin(
    var exchange: String = "", // 거래소
    var symbol: String = "",
    var pair: String? = "BTC",
    var quantity: Double = 0.0, // 코인수량
    var avgPrice: Double = 0.0, // 평단
    var purchaseAmount: Double = 0.0, // 매수금액 (코인수량*평단)
    var percent:Double= 0.0, // 수익률 (1- (lastPrice/avgPrice)*100)
    var profit:Double=0.0 // 수익금 (purchasAmount * percent)

) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}