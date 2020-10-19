package com.yj.coinreturns.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.PublicBinanceClient
import com.cluttered.cryptocurrency.model.account.AccountSnapshot
import com.cluttered.cryptocurrency.model.marketdata.CandlestickInterval
import com.cluttered.cryptocurrency.websocket.RxWebSocketEvent
import com.mvvm.mybinance.model.Coin
import com.yj.coinreturns.model.App
import com.yj.coinreturns.repository.CoinRepository
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.xerces.dom.DOMMessageFormatter.init
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher.SECRET_KEY


class BinanceViewModel(application: Application) : AndroidViewModel(application) {


    /**
     * Pair Priority: 1. USDT, 2.BTC, 3.BNB
     * allSymbolPair: Binance 의 모든 Symbol, Pair 쌍을 가짐
     * isAllSymbolPairSetting: allSymbolPair 세팅이 끝나면 true
     *
     */

    private var client = BinanceClient.create(App.prefs.apiBinance!!, App.prefs.secretBinance!!)
    private var publicBinanceClient = PublicBinanceClient.create()

    private val mCoinRepository: CoinRepository
    private var mCoinList: LiveData<MutableList<Coin>>
    private var lastCheckTimestamp: Long = 0
    private val allSymbolPair = mutableListOf<Pair<String, String>>()
    private var isAllSymbolPairSetting = MutableLiveData(false)

    var assetList = mutableListOf<Coin>()


    var test = MutableLiveData(hashMapOf<String, String>())

    init {
        getAllSymbolPair()
        mCoinRepository = CoinRepository(application, "binance")
        mCoinList = mCoinRepository.getAllFromRoom()
        lastCheckTimestamp= App.prefs.lastCheckTimeStampBinance
    }

    fun getAllFromRoom() = mCoinList
    fun getLastCheckTimestamp()= lastCheckTimestamp
    fun getIsAllSymbolPairSetting() = isAllSymbolPairSetting


    /**
     * 가장 처음으로 로그인 했을때.
     * 현재 계정 Asset 불러온 뒤, 평단은 현재시간으로 맞춤 ( 추후 RecyclerView 에서 클릭하여 직접 수정 유도 )
     */
    @SuppressLint("CheckResult")
    fun initFirstAsset() {
         client.account.snapshot()
            .map { AccountSnapshot ->
                AccountSnapshot.balances.filter { it.free.toDouble() != 0.00000000 }
                    .filter { it.asset != "USDT" }.map { Pair(it.asset, it.free.toDouble()) }
            }
            .flatMap { list -> Observable.fromIterable(list) }
            .flatMap { inputPair ->
                var symbol = inputPair.first
                var quantity = inputPair.second
                var pair: String? = null


                if (allSymbolPair.contains(Pair(symbol, "USDT"))) pair = "USDT"
                else if (allSymbolPair.contains(Pair(symbol, "BTC"))) pair = "BTC"
                else if (allSymbolPair.contains(Pair(symbol, "BNB"))) pair = "BNB"

                if (pair != null) // pair 가 USDT/BTC/BNB 에도 없으면 그냥 안쓸것.
                    assetList.add(
                        Coin(
                            exchange = "binance",
                            symbol = symbol,
                            pair = pair,
                            quantity = quantity
                        )
                    )


                client.marketData.tickerPrice("$symbol$pair")
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ ticker ->
                var symbol = ticker.symbol
                var price = ticker.price.toDouble()

                var coin = assetList.first { c -> "${c.symbol}${c.pair}" == symbol }
                coin.avgPrice = price
                coin.purchaseAmount = price * coin.quantity

            }, {
                Log.d("fhrm", "BinanceViewModel -test(),    error: ${it.message}")
            })

    }

    /**
     * asset.asset -> symbol ( not include pair )
     * asset.free -> amount
     */
    @SuppressLint("CheckResult")
    fun getAsset() {
        client.account.snapshot()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                it.balances.forEach { asset ->
                    if (asset.free.toDouble() != 0.00000000) {
                        var symbol = asset.asset
                        var quantity = asset.free.toDouble()
                    }
                }

            }

    }



    @SuppressLint("CheckResult")
    fun applyOrderHistory(inputSymbol: String) {
        client.account.allOrders(inputSymbol)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it ->
                it.forEachIndexed { index, order ->
                    Log.d("fhrm", "TestActivity -test(),    index: ${index}, order: ${order}")
                }
            }
    }

    @SuppressLint("CheckResult")
    fun getCurrentTime() {
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getCurrentTime(),    serverTime: ${it.serverTime}, systemTime: ${System.currentTimeMillis()}"
                )
            })
    }


    /**
     * timeStamp 시간대에 해당 symbol 의 평균가격을 구해줌
     */
    @SuppressLint("CheckResult")
    fun getSpecificTimeAvgPrice(symbol: String, timestamp: Long) {
        // 특정시간 평균 가격 구하는 코드
        client.marketData.candlesticks(
            symbol = symbol,
            interval = CandlestickInterval.MINUTES_1,
            startTime = timestamp,
            endTime = timestamp + 60000
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                it.forEachIndexed { index, candlestick ->
                    Log.d(
                        "fhrm",
                        "BinanceViewModel -test(),    index: ${index}, candlestick.low: ${candlestick.low}, candlestick.high: ${candlestick.high}}"
                    )
                }
                var avgPirce = (it[0].high + it[0].low) / 2.toBigDecimal()
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getSpecificTimeAvgPrice(),    avgPirce: ${avgPirce}"
                )
            }
    }


    /**
     * getLastPriceFromUrl 이거 쓸지, getCurrentPriceFromAPI 이거쓸지 고민. 같은기능임
     */
    @Throws(Throwable::class)
    fun getLastPriceFromUrl(symbol: String, pair: String) {

        viewModelScope.launch(Dispatchers.IO) {
            val huc =
                URL("https://api.binance.com/api/v1/ticker/24hr?symbol=${symbol?.toUpperCase()}${pair.toUpperCase()}").openConnection() as HttpURLConnection
            huc.requestMethod = "GET"
            huc.addRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0"
            )
            huc.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            huc.connect()
            var `in`: InputStream? = null
            if (huc.responseCode !== 200) {
                `in` = huc.errorStream
            } else {
                `in` = huc.inputStream
            }
            val br = BufferedReader(InputStreamReader(`in`, "UTF-8"))
            var line: String?
            val sb = StringBuilder()
            line = br.readLine()
            while (line != null) {
                sb.append(line)
                line = br.readLine()
            }
            br.close()
            var lastPrice = JSONObject(sb.toString().trimIndent()).getString("lastPrice")
            test.postValue(hashMapOf("$symbol$pair" to lastPrice))
        }
    }

    @SuppressLint("CheckResult")
    fun getCurrentPriceFromAPI(symbol: String, pair: String) {
        client.marketData.tickerPrice("$symbol$pair")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.d("fhrm", "BinanceViewModel -test(),    TickerPrice: ${it}")
            })
    }


    @SuppressLint("CheckResult")
    fun getAllSymbolPair() {
        client.general.exchangeInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it ->
                it.symbols.forEachIndexed { index, symbol ->
                    allSymbolPair.add(Pair(symbol.baseAsset, symbol.quoteAsset))
                }
                isAllSymbolPairSetting.value = true
            }
    }

    class Factory(val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return BinanceViewModel(application) as T
        }
    }
}