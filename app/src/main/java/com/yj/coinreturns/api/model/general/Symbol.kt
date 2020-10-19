package com.cluttered.cryptocurrency.model.general

import com.cluttered.cryptocurrency.model.account.OrderType

data class Symbol(
        val symbol: String,
        val status: SymbolStatus,
        val baseAsset: String,
        val baseAssetPrecision: Int,
        val quoteAsset: String,
        val quotePrecision: Int,
        val orderTypes: List<OrderType>,
        val icebergAllowed: Boolean,
        val filters: List<Any>
)