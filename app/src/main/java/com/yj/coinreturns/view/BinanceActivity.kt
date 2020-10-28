package com.yj.coinreturns.view

import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.yj.coinreturns.R
import com.yj.coinreturns.databinding.ActivityBinanceBinding
import com.yj.coinreturns.viewModel.BinanceViewModel
import kotlinx.android.synthetic.main.activity_binance.*

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
            if (isSettingDone) {
                if (binanceViewModel.getLastCheckTimestamp() == 0L) { //첫로그인이라면
                    binanceViewModel.initFirstAsset()
                    waitGetHaveToCheckSymbol()
//                    waitRefreshProfit()
                } else {
                    getHaveToCheckSymbol()
//                    refreshProfit()
                }

            }
        })

        binanceViewModel.getAllFromRoom().observe(this, Observer { list ->
            list.forEachIndexed { index, coin ->
                Log.d("fhrm", "BinanceActivity -onCreate(),    index: ${index}, coin: ${coin}")
            }
            Log.d("fhrm", " ")
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
        }
        binance_btn_test7.setOnClickListener {
        }
        binance_btn_test8.setOnClickListener {
        }
        binance_btn_test9.setOnClickListener {
            binanceViewModel.getHaveToCheckSymbol()

        }
        binance_btn_test10.setOnClickListener {
            binanceViewModel.getOrderHistory("ETHBTC")
        }
        binance_btn_test11.setOnClickListener {
            binanceViewModel.test()
        }


    }


    private val mDelayHandler: Handler by lazy {
        Handler()
    }

    private fun waitGetHaveToCheckSymbol() {
        mDelayHandler.postDelayed(::getHaveToCheckSymbol, 13000) // 10초 후에 showGuest 함수를 실행한다.
    }

    private fun waitRefreshProfit() {
        mDelayHandler.postDelayed(::refreshProfit, 1000) // 10초 후에 showGuest 함수를 실행한다.
    }


    private fun refreshProfit() {
        binanceViewModel.refreshProfit()
        waitRefreshProfit()
    }

    private fun getHaveToCheckSymbol() {
        binanceViewModel.getHaveToCheckSymbol()
        waitGetHaveToCheckSymbol() // 코드 실행뒤에 계속해서 반복하도록 작업한다.
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("fhrm", "BinanceActivity -onDestroy(),    : ")
    }
}

