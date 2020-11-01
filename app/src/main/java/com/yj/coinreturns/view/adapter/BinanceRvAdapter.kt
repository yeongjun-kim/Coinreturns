package com.yj.coinreturns.view.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mvvm.mybinance.model.Coin
import com.yj.coinreturns.R
import com.yj.coinreturns.databinding.ItemBinanceRvBinding
import java.text.DecimalFormat


class BinanceRvAdapter : RecyclerView.Adapter<BinanceRvAdapter.CustomViewHolder>() {
    var coinList: MutableList<Coin> = mutableListOf()
    var listener: ClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        return CustomViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_binance_rv,
                parent,
                false
            ), listener
        )
    }

    override fun getItemCount(): Int = coinList.size

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        holder.bind(coinList[position])
    }

    fun setList(inputList: MutableList<Coin>) {
        val diffResult = DiffUtil.calculateDiff(BinanceRvDiffCallback(coinList, inputList))
        coinList = inputList
        diffResult.dispatchUpdatesTo(this)
        notifyDataSetChanged()
    }


    interface ClickListener {
        fun onShortClick(position: Int)
    }

    class CustomViewHolder(val binding: ItemBinanceRvBinding, val listener: ClickListener?) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener { listener?.onShortClick(adapterPosition) }

        }

        fun bind(item: Coin) {
            val color = if (item.percent >= 0) "#C14040" else "#387DF0"
            binding.apply {
                itemBinanceTvP.setTextColor(Color.parseColor(color))
                itemBinanceTvPercent.setTextColor(Color.parseColor(color))
                itemBinanceTvProfit.setTextColor(Color.parseColor(color))
                itemBinanceTemp6.setTextColor(Color.parseColor(color))
                coin = item
            }
        }
    }

}