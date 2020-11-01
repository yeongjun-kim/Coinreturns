package com.yj.coinreturns.view.adapter

import android.widget.EditText
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

    /**
     * 소수점 포함하여 총 15자리 맞추기.
     */
    @JvmStatic
    @BindingAdapter("digitEdit")
    fun setDigitEdit(view: EditText, quantity: Double) {
        var bigdecimal = BigDecimal(quantity)
        var point = bigdecimal.toString().indexOf('.')
        var s = ""

        if (point >= 8)
            s = bigdecimal.setScale(15 - point, RoundingMode.HALF_UP).toString()
        else
            s = bigdecimal.setScale(8, RoundingMode.HALF_UP).toString()
        view.setText(s)
        view.setSelection(s.length)
    }


    @JvmStatic
    @BindingAdapter("percent")
    fun setPercent(view: TextView, quantity: Double) {
        lateinit var s: String
        if (quantity > 0.0) s = "+${String.format("%.2f", quantity)}"
        else s = "${String.format("%.2f", quantity)}"
        view.text = "${s}%"
    }

    @JvmStatic
    @BindingAdapter("profit")
    fun setProfit(view: TextView, profit: Double) {
        var bigdecimal = BigDecimal(profit)
        var point = bigdecimal.toString().indexOf('.')
        var s = ""

        if (point >= 8)
            s = bigdecimal.setScale(15 - point, RoundingMode.HALF_UP).toString()
        else
            s = String.format("%.8f",bigdecimal.setScale(8, RoundingMode.HALF_UP))
        if (profit >= 0.0)
            view.text = dividePorint("+$s")
        else
            view.text = "-${dividePorint("$s")}"
    }

    @JvmStatic
    @BindingAdapter("deleteMessage")
    fun setDeleteMessage(view: TextView, s: String) {
        view.text = "* If you wnat to delete $s from the list,\n   enter 0 at the Balance or Average Price."
    }

    fun dividePorint(s: String): String {
        var temp = s.split('.')
        if(temp.size<2) return "0"

        if (s[0] == '+') return "+${formatter.format(BigDecimal(temp[0]))}.${temp[1]}"
        else return "${formatter.format(BigDecimal(temp[0].replace("-","")))}.${temp[1]}"
    }





}