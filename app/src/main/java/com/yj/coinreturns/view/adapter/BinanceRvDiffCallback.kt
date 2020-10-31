package com.yj.coinreturns.view.adapter

import androidx.recyclerview.widget.DiffUtil
import com.mvvm.mybinance.model.Coin

class BinanceRvDiffCallback (var oldList: MutableList<Coin>, var newList: MutableList<Coin>) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldList[oldItemPosition].id == newList[newItemPosition].id

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldList[oldItemPosition] == newList[newItemPosition]

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

}