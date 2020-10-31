package com.yj.coinreturns.view.adapter

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.DecimalFormat
import kotlin.math.round

object BindingAdapters {
    // satosi: 0.00001853 (8자리)


    @JvmStatic
    @BindingAdapter("dotEight")
    fun setDotEight(view: TextView, quantity: Double) {
        view.text  = String.format("%.8f",quantity)
    }

    @JvmStatic
    @BindingAdapter("percent")
    fun setPercent(view: TextView, quantity: Double) {
        lateinit var s:String
        if(quantity > 0.0) s = "+${String.format("%.2f",quantity)}"
        else s = "${String.format("%.2f",quantity)}"
        view.text  = s
    }

    @JvmStatic
    @BindingAdapter("profit")
    fun setProfit(view: TextView, quantity: Double) {
        lateinit var s:String
        if(quantity > 0.0) s = "+${String.format("%.8f",quantity)}"
        else s = "${String.format("%.8f",quantity)}"
        view.text  = s
    }


}