package com.yj.coinreturns.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.PublicBinanceClient
import com.yj.coinreturns.model.App
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.crypto.Cipher.SECRET_KEY

class LoginViewModel(application: Application) : AndroidViewModel(application) {


    var binanceApiKey = App.prefs.apiBinance
    var binanceSecretKey = App.prefs.secretBinance
    var huobiApiKey = App.prefs.apiHuobi
    var huobiSecretKey = App.prefs.secretHuobi
    var coinbaseApiKey = App.prefs.apiCoinBase
    var coinbaseSecretKey = App.prefs.secretCoinbase
    var tempApiKey = ""
    var tempSecretKey = ""

    var isBinanceLoginSuccess = MutableLiveData(-1) // Default -1, Success 1, Show PopupDialog 0
    var isHuobiLoginSuccess = MutableLiveData(-1)
    var isCoinbaseLoginSuccess = MutableLiveData(-1)


    @SuppressLint("CheckResult")
    fun checkBinanceKey() {
        if (binanceApiKey.isNullOrBlank() || binanceSecretKey.isNullOrBlank()) {
            isBinanceLoginSuccess.value = 0
        } else {
            var client = BinanceClient.create(binanceApiKey!!, binanceSecretKey!!)

            client.account.allOrders("BTCUSDT")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    isBinanceLoginSuccess.value = 1
                    App.prefs.apiBinance = binanceApiKey
                    App.prefs.secretBinance = binanceSecretKey
                }, {
                    isBinanceLoginSuccess.value = 0
                })
        }
    }

    fun checkHuobiKey() {
        //TODO()
    }

    fun checkCoinbaseKey() {
        //TODO()
    }

    fun setBinanceKey() {
        binanceApiKey = tempApiKey
        binanceSecretKey = tempSecretKey
    }

    fun setHuobiKey() {
        huobiApiKey = tempApiKey
        huobiSecretKey = tempSecretKey
    }

    fun setCoinbaseKey() {
        coinbaseApiKey = tempApiKey
        coinbaseSecretKey = tempSecretKey
    }


    class Factory(val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return LoginViewModel(application) as T
        }
    }

}