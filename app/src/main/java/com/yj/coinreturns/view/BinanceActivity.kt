package com.yj.coinreturns.view

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.PublicBinanceClient
import com.yj.coinreturns.R
import com.yj.coinreturns.databinding.ActivityBinanceBinding
import com.yj.coinreturns.model.App
import com.yj.coinreturns.viewModel.BinanceViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.*
import kotlinx.android.synthetic.main.activity_binance.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class BinanceActivity : AppCompatActivity() {


    lateinit var binding: ActivityBinanceBinding
    lateinit var binanceViewModel: BinanceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binanceViewModel = ViewModelProvider(
            this,
            BinanceViewModel.Factory(application)
        ).get(BinanceViewModel::class.java)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_binance)
        binding.apply {
            lifecycleOwner = this@BinanceActivity
            bvm = binanceViewModel
        }


        binanceViewModel.getIsAllSymbolPairSetting().observe(this, Observer { isSettingDone ->
            if(isSettingDone){
                if(binanceViewModel.getLastCheckTimestamp() == 0L){
                    Log.d("fhrm", "BinanceActivity -onCreate(),    : firstLogin")
                    binanceViewModel.initFirstAsset()
                }
                initCoroutine()
            }
        })

        binanceViewModel.getAllFromRoom().observe(this, Observer { list ->
//            list.forEachIndexed { index, coin ->
//                Log.d("fhrm", "BinanceActivity -onCreate(),    index: ${index}, coin: ${coin}")
//            }
//            Log.d("fhrm", " ")
        })



        // *********** TEST *********** //
        binance_btn_test1.setOnClickListener {
        }
        binance_btn_test2.setOnClickListener {
        }
        binance_btn_test3.setOnClickListener {
            binanceViewModel.getCurrentTime()
            Log.d(
                "fhrm",
                "BinanceActivity -onCreate(),    getLastCheckTimestamp: ${binanceViewModel.getLastCheckTimestamp()}"
            )
        }
        binance_btn_test4.setOnClickListener {
            binanceViewModel.getAllFromRoom()
        }
        binance_btn_test5.setOnClickListener {
            binanceViewModel.getAllFromRoom().value!!.forEachIndexed { index, coin ->
                Log.d("fhrm", "BinanceActivity -onCreate(),    index: ${index}, coin: ${coin}")
            }
        }
        binance_btn_test6.setOnClickListener {
            binanceViewModel.getOrderHistory("XRPUSDT")
        }
        binance_btn_test7.setOnClickListener {
        }
        binance_btn_test8.setOnClickListener {
        }
        binance_btn_test9.setOnClickListener {
        }
        binance_btn_test10.setOnClickListener {
            binanceViewModel.getOrderHistory("XRPUSDT")
        }
        binance_btn_test11.setOnClickListener {
//            binanceViewModel.refreshProfit()
            binanceViewModel.gatherChangeAfterLastLogin()

        }



    }

    private fun initCoroutine() {
        GlobalScope.launch(Dispatchers.Main) {
//            while (true) {
//                delay(1000L)
//            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                binanceViewModel.gatherChangeAfterLastLogin()
                delay(3000L)
            }
        }
//        GlobalScope.launch(Dispatchers.IO) {
//            while (true) {
//                binanceViewModel.refreshProfit()
//                delay(1000L)
//            }
//        }

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("fhrm", "BinanceActivity -onDestroy(),    : ")
    }
}

