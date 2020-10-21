package com.yj.coinreturns.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.PublicBinanceClient
import com.cluttered.cryptocurrency.model.account.AccountSnapshot
import com.cluttered.cryptocurrency.model.marketdata.CandlestickInterval
import com.cluttered.cryptocurrency.services.AccountService
import com.cluttered.cryptocurrency.services.WithdrawService.Companion.ONE_MINUTE_IN_MILLIS
import com.cluttered.cryptocurrency.websocket.RxWebSocketEvent
import com.mvvm.mybinance.model.Coin
import com.yj.coinreturns.model.App
import com.yj.coinreturns.repository.CoinRepository
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import org.apache.xerces.dom.DOMMessageFormatter.init
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
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


    init {
        getAllSymbolPair()
        mCoinRepository = CoinRepository(application, "binance")
        mCoinList = mCoinRepository.getAllFromRoom()
        lastCheckTimestamp = App.prefs.lastCheckTimeStampBinance
    }

    fun getAllFromRoom() = mCoinList
    fun getLastCheckTimestamp() = lastCheckTimestamp
    fun getIsAllSymbolPairSetting() = isAllSymbolPairSetting


    @SuppressLint("CheckResult")
    fun gatherChangeAfterLastLogin() {
        var allSymbol = mCoinList.value!!.map { it -> it.symbol }.toMutableList()

//        client.account.snapshot()
//            .map { AccountSnapshot ->
//                AccountSnapshot.balances.filter { it.free.toDouble() != 0.00000000 }
//                    .filter { it.asset != "USDT" }.map { it.asset }.toMutableList()
//            }.map{
//                allSymbol.addAll(it)
//                allSymbol.distinct()
//            }
//            .flatMap {symbolList ->
//                Observable.fromIterable(symbolList)
//            }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({
//                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    symbol: ${it}")
//            },{
//                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    error: ${it.message}")
//            },{
//                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    finish")
//            })


        /**
         * 얘가 룸, Asset 에 다 들어있는 symbol 리스트 갖고있는 Observable
         */
        var symbolSettingObservable = client.account.snapshot()
            .map { AccountSnapshot ->
                AccountSnapshot.balances.filter { it.free.toDouble() != 0.00000000 }
                    .filter { it.asset != "USDT" }.map { it.asset }.toMutableList()
            }.map {
                allSymbol.addAll(it)
                allSymbol.distinct()
            }

        /**************************************************************************************
         **************************************************************************************
         **************************************************************************************
         **************************************************************************************
         **************************************************************************************
         */
        symbolSettingObservable.flatMap { symbolList ->
            Observable.fromIterable(symbolList)
        }.flatMap { s ->
            client.withdraw.depositHistory(asset = s, timestamp = lastCheckTimestamp)
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                // 이러면 여기에 Room, Asset에 들어있는 자산에 대한 입금 내역만 나옴. 얘네를
            },{
                // 여긴 에러출력
            },{
                // 여기에다 이 함수내에 변수 하나 설정해놓고 플러스1 해주셈.
                // 그리고 출금,입금,오더 에 대한 list 다 완성되고, 플러스 1 해준 변수가 3이 되면
                // 그거 if(변수==3) 이면 완성된 list 변수로 넘기고
                // 그것들에대한 해당시간대 시세랑 또 계산해서 업데이트 하는거 할것.
            })
        /**************************************************************************************
         **************************************************************************************
         **************************************************************************************
         **************************************************************************************
         **************************************************************************************
         */



    }

    fun test() {
    }

    @SuppressLint("CheckResult")
    fun getDepositHistory() {
        client.withdraw.depositHistory(
            null, // S
            null,
            1602833760000, // 이곳에 lastcheck timestamp 넣으면 됨
            null,
            AccountService.ONE_MINUTE_IN_MILLIS,
            Instant.now().toEpochMilli()
        ).map { it.depositList }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                it.sortedBy { it.insertTime }
                it.forEach {
                    Log.d("fhrm", "BinanceViewModel -getDepositHistory(),    : ${it}")
                }
            }, { e ->
                Log.d("fhrm", "TestActivity -onCreate(),    : ${e.message}")
            }
            )
    }

    fun withraw() {
        client.withdraw.withdrawHistory(
            null,
            null,
            null,
            null,
            ONE_MINUTE_IN_MILLIS,
            Instant.now().toEpochMilli()
        )
            .map { it.withdrawList }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                it.forEach {
                    Log.d("fhrm", "BinanceViewModel -withdraw(),    : ${it}")
                }
            }, { e ->
                Log.d("fhrm", "TestActivity -onCreate(),    : ${e.message}")
            }
            )
    }


    fun deleteCoinFromDB(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mCoinRepository.delete(symbol)
        }
    }

    /**
     * 주기적으로 Asset 을 체크하여, 기존 mCoinList 와 비교하여 새로운 Asset이
     * ADD   : [추가] -> 새로운 코인 매수 또는 입금
     * DELETE: [삭제] -> 기존 코인 매도
     * CHANGE: [수량증가] -> 기존 코인 추매 또는 입금 (거래내역 추적하여 평단 수정 작업 필요)
     * CHANGE: [수량감소] -> 평단은 그대로이며 그냥 quantity 만 줄여주면됨
     */
    @SuppressLint("CheckResult")
    fun checkIsAssetUpdate() {

        client.account.snapshot()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                //
//                var newAssetList =
//                    it.balances.filter { it.free.toDouble() != 0.00000000 && it.asset != "USDT" }
//                        .map { assetObject(it.asset, it.free.toDouble()) }


                var newAssetList = listOf(
                    assetObject(symbol = "XRP", quantity = 5.668587),
                    assetObject(symbol = "ADD1", quantity = 1.668587),
                    assetObject(symbol = "ADD2", quantity = 1.668587)
                )

                newAssetList.forEach {
                    Log.d("fhrm", "BinanceViewModel -checkIsAssetUpdate(),    : ${it}")
                }

                var haveChangeSymbol = mutableListOf(listOf<Any>())


                newAssetList.forEach { asset ->

                    var isInRoom = mCoinList.value!!.any { room -> asset.symbol == room.symbol }

                    if (isInRoom) {
                        var isSameQuantity =
                            mCoinList.value!!.any { room -> asset.symbol == room.symbol && asset.quantity == room.quantity }
                        if (!isSameQuantity) {
                            haveChangeSymbol.add(
                                listOf(
                                    "CHANGE",
                                    asset.symbol,
                                    asset.quantity
                                )
                            ) // ASSET, ROOM 존재, Quantity 다름 -> [수정]

                        }
                    } else {
                        Log.d(
                            "fhrm",
                            "BinanceViewModel -checkIsAssetUpdate(),    add: ${asset.symbol}"
                        )
                        haveChangeSymbol.add(
                            listOf(
                                "ADD",
                                asset.symbol,
                                asset.quantity
                            )
                        ) // ASSET 존재, ROOM 미존재 -> [추가]
                    }

                }


                mCoinList.value!!.forEach { room ->
                    var isInAsset = newAssetList.any { asset -> room.symbol == asset.symbol }
                    if (!isInAsset) haveChangeSymbol.add(
                        listOf(
                            "DELETE",
                            room.symbol
                        )
                    ) // ASSET 미존재, ROOM 존재 -> [삭제]
                }

                Log.d(
                    "fhrm",
                    "BinanceViewModel -checkIsAssetUpdate(),    haveChangeSymbol.size: ${haveChangeSymbol.size}"
                )
                haveChangeSymbol.forEach lit@{ list ->
                    if (list.isNullOrEmpty()) return@lit
                    if (list[0] == "ADD") { // list[1]: Coin.symbol, list[2]: Coin.quantity

                    } else if (list[0] == "CHANGE") { // list[1]: Coin.symbol, list[2]: Coin.quantity
                    } else if (list[0] == "DELETE") { // list[1]: Coin.symbol
                        deleteCoinFromDB(list[1].toString())
                    }
                }
            }

    }


    @SuppressLint("CheckResult")
    fun getOrderHistory(inputSymbol: String) {
        client.account.allOrders(inputSymbol)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it ->
                it.forEachIndexed { index, order ->
                    Log.d("fhrm", "TestActivity -test(),    index: ${index}, order: ${order}")
                }
            }
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


    fun refreshProfit() {
        mCoinList.value?.forEach { coin ->
            client.marketData.tickerPrice("${coin.symbol}${coin.pair}")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ ticker ->
                    var currentPrice = ticker.price.toDouble()
                    var percent =
                        String.format("%.2f", ((currentPrice / coin.avgPrice) - 1) * 100).toDouble()
                    var profilt = coin.purchaseAmount * (percent / 100)

                    coin.percent = percent
                    coin.profit = profilt

                    insertCoinToDB(coin)
                }, { e ->
                    Log.d("fhrm", "BinanceViewModel -test(),    error: ${e.message}")
                })
        }
    }


    /**
     * 가장 처음으로 로그인 했을때.
     * 현재 계정 Asset 불러온 뒤, 평단은 현재시간으로 맞춤 ( 추후 RecyclerView 에서 클릭하여 직접 수정 유도 )
     */
    @SuppressLint("CheckResult")
    fun initFirstAsset() {
        var tempList = mutableListOf<Coin>()


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
                    tempList.add(
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

                var coin = tempList.first { c -> "${c.symbol}${c.pair}" == symbol }
                coin.avgPrice = price
                coin.purchaseAmount = price * coin.quantity

                insertCoinToDB(coin)
                setLastCheckTimestamp()

            }, {
                Log.d("fhrm", "BinanceViewModel -test(),    error: ${it.message}")
            })

    }


    fun insertCoinToDB(coin: Coin) {
        viewModelScope.launch(Dispatchers.IO) {
            mCoinRepository.insert(coin)
        }
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
                        Log.d(
                            "fhrm",
                            "BinanceViewModel -getAsset(),    symbol: ${symbol}, quantity: ${quantity}"
                        )
                    }
                }

            }
    }


    @SuppressLint("CheckResult")
    fun setLastCheckTimestamp() {
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                App.prefs.lastCheckTimeStampBinance = it.serverTime
                lastCheckTimestamp = it.serverTime
            }
    }

    @SuppressLint("CheckResult")
    fun getCurrentTime() {
        var a = 0

        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getCurrentTime(),    it.serverTime: ${it.serverTime}"
                )
            }
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

                allSymbolPair.forEach {
                    Log.d(
                        "fhrm",
                        "BinanceViewModel -getAllSymbolPair(),    symbol: ${it.first}, pair: ${it.second}"
                    )
                }
                isAllSymbolPairSetting.value = true
            }
    }


    data class assetObject(
        var symbol: String,
        var quantity: Double
    )

    class Factory(val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return BinanceViewModel(application) as T
        }
    }
}