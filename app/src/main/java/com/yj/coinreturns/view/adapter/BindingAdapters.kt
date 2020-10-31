package com.yj.coinreturns.view.adapter

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.round

object BindingAdapters {
    val formatter = DecimalFormat("###,###")

    /**
     * 소수점 포함하여 총 15자리 맞추기.
     */
    @JvmStatic
    @BindingAdapter("digit")
    fun setDigit(view: TextView, quantity: Double) {
        var bigdecimal = BigDecimal(quantity)
        var point = bigdecimal.toString().indexOf('.')
        var s = ""

        if (point >= 8)
            s = bigdecimal.setScale(15 - point, RoundingMode.HALF_UP).toString()
        else
            s = bigdecimal.setScale(8, RoundingMode.HALF_UP).toString()
        view.text = dividePorint(s)
    }

    @JvmStatic
    @BindingAdapter("percent")
    fun setPercent(view: TextView, quantity: Double) {
        lateinit var s: String
        if (quantity > 0.0) s = "+${String.format("%.2f", quantity)}"
        else s = "${String.format("%.2f", quantity)}"
        view.text = s
    }

    @JvmStatic
    @BindingAdapter("profit")
    fun setProfit(view: TextView, quantity: Double) {
        var bigdecimal = BigDecimal(quantity)
        var point = bigdecimal.toString().indexOf('.')
        var s = ""

        if (point >= 8)
            s = bigdecimal.setScale(15 - point, RoundingMode.HALF_UP).toString()
        else
            s = bigdecimal.setScale(8, RoundingMode.HALF_UP).toString()

        if (s.toBigDecimal() > 0.toBigDecimal())
            view.text = dividePorint("+$s")
        else
            view.text = dividePorint(s)
    }

    fun dividePorint(s: String): String {
        var temp = s.split('.')

        val formatter = DecimalFormat("###,###")
        if (s[0] == '+') return "+${formatter.format(BigDecimal(temp[0]))}.${temp[1]}"
        else return "${formatter.format(BigDecimal(temp[0]))}.${temp[1]}"
    }


}