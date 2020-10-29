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
}