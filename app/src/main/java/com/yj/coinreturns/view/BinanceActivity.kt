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
            if (isSettingDone && binanceViewModel.getLastCheckTimestamp() == 0L) { // 첫 로그인시
//                Log.d("fhrm", "BinanceActivity -onCreate(),    : first login")
                binanceViewModel.initFirstAsset()
            } else { // 아닐시
//                Log.d("fhrm", "BinanceActivity -onCreate(),    : not first login")
            }
        })

        binanceViewModel.getAllFromRoom().observe(this, Observer { list ->
            list.forEachIndexed { index, coin ->
                Log.d("fhrm", "BinanceActivity -onCreate(),    index: ${index}, coin: ${coin}")
            }
        })

        /**
         *
         * coroutine ( 주기적으로 리프레쉬하는 함수 만들자리 )
         * -> viewmodel.refreshProfit
         * -> viewmodel.setLastCheckTimestamp
         *
         * observable은 하나로 묶는거 고려
         *
         */

        // *********** TEST *********** //
        binance_btn_test1.setOnClickListener {
            binanceViewModel.getAsset()
        }
        binance_btn_test2.setOnClickListener {
//            binanceViewModel.checkIsAssetUpdate()
//            binanceViewModel.test()
            binanceViewModel.getDepositHistory()
//            binanceViewModel.withraw()
//            binanceViewModel.getSpecificTimeAvgPrice("BTCUSDT",1603194796570)
//            binanceViewModel.gatherChangeAfterLastLogin()
        }

        binance_btn_test3.setOnClickListener {
            binanceViewModel.getCurrentTime()
            Log.d("fhrm", "BinanceActivity -onCreate(),    getLastCheckTimestamp: ${binanceViewModel.getLastCheckTimestamp()}")
        }

        binance_btn_test4.setOnClickListener {
            binanceViewModel.getAllSymbolPair()
        }
        binance_btn_test5.setOnClickListener {
            binanceViewModel.getAllFromRoom().value!!.forEachIndexed { index, coin ->
                Log.d("fhrm","BinanceActivity -onCreate(),    index: ${index}, coin: ${coin}")}
        }
        binance_btn_test6.setOnClickListener {
            binanceViewModel.getOrderHistory("XRPUSDT")
        }
// **************************** //


    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("fhrm", "BinanceActivity -onDestroy(),    : ")
    }
}