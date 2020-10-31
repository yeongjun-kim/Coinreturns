package com.yj.coinreturns.view

import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.yj.coinreturns.R
import com.yj.coinreturns.databinding.ActivityBinanceBinding
import com.yj.coinreturns.test.test
import com.yj.coinreturns.view.adapter.BinanceRvAdapter
import com.yj.coinreturns.viewModel.BinanceViewModel
import kotlinx.android.synthetic.main.activity_binance.*

class BinanceActivity : AppCompatActivity() {


    lateinit var binding: ActivityBinanceBinding
    lateinit var binanceViewModel: BinanceViewModel


    private val mAdapter = BinanceRvAdapter()
    private var isRunningGethaveToCheckSymbol = true
    private var isRunningRefrshProfit = true

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
                    waitRefreshProfit()
                } else {
                    mDelayHandler.postDelayed(getHaveToCheckSymbol, 1500) // 첫로그인이 아니라면 1초뒤 바로 시작
                    mDelayHandler.postDelayed(refreshProfit, 500)
                }

            }
        })

        binanceViewModel.getAllFromRoom().observe(this, Observer { list ->
            mAdapter.setList(list)
        })


        initRv()


        // *********** TEST *********** //
        binance_btn_test1.setOnClickListener {
            binanceViewModel.test1()
        }
        binance_btn_test2.setOnClickListener {
            binanceViewModel.test2()
        }
        binance_btn_test3.setOnClickListener {
            //            binanceViewModel.test3(test.text.toString())
        }
        binance_btn_test4.setOnClickListener {
            binanceViewModel.test4()
            mDelayHandler.removeCallbacks(getHaveToCheckSymbol)
        }

    }

    private fun initRv() {
        binding.binanceRv.apply {
            layoutManager = LinearLayoutManager(this@BinanceActivity)
            setHasFixedSize(true)
            adapter = mAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isRunningGethaveToCheckSymbol) mDelayHandler.postDelayed(getHaveToCheckSymbol, 1000)
        if (!isRunningRefrshProfit) waitRefreshProfit()

    }

    override fun onPause() {
        super.onPause()
        mDelayHandler.removeCallbacks(getHaveToCheckSymbol)
        mDelayHandler.removeCallbacks(refreshProfit)
        isRunningRefrshProfit = false
        isRunningGethaveToCheckSymbol = false
    }

    private val mDelayHandler: Handler by lazy {
        Handler()
    }

    private fun waitGetHaveToCheckSymbol() {

        mDelayHandler.postDelayed(getHaveToCheckSymbol, 13000) // 15초 후에 showGuest 함수를 실행한다.
    }

    private fun waitRefreshProfit() {
        mDelayHandler.postDelayed(refreshProfit, 3000) // 10초 후에 showGuest 함수를 실행한다.
    }


    private var refreshProfit = Runnable {
        isRunningRefrshProfit = true
        binanceViewModel.refreshProfit()
        waitRefreshProfit()
    }

    private var getHaveToCheckSymbol = Runnable {
        isRunningGethaveToCheckSymbol = true
        binanceViewModel.getHaveToCheckSymbol()
        waitGetHaveToCheckSymbol()
    }


}

