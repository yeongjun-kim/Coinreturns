package com.cluttered.cryptocurrency.serial

import com.cluttered.cryptocurrency.model.marketdata.CandlestickInterval
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class CandlestickIntervalDeserializer : JsonDeserializer<CandlestickInterval> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CandlestickInterval {
        return CandlestickInterval.fromDisplay(json.asString)
    }
}