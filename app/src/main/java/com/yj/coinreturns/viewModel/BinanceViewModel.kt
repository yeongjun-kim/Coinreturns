package com.yj.coinreturns.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.model.account.OrderStatus
import com.cluttered.cryptocurrency.model.marketdata.CandlestickInterval
import com.cluttered.cryptocurrency.model.withdraw.WithdrawStatus
import com.mvvm.mybinance.model.Coin
import com.yj.coinreturns.model.App
import com.yj.coinreturns.repository.CoinRepository
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*


class BinanceViewModel(application: Application) : AndroidViewModel(application) {


    /**
     * Pair Priority: 1. USDT, 2.BTC, 3.BNB
     * allSymbolPair: Binance 의 모든 Symbol, Pair 쌍을 가짐
     * isAllSymbolPairSetting: allSymbolPair 세팅이 끝나면 true
     * changeList: 입금, 출금, 체결된 Order에 대한 변화를 넣는 List
     *
     */

    private var client = BinanceClient.create(App.prefs.apiBinance!!, App.prefs.secretBinance!!)

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


    /**
     * gatherChangeAfterLastLogin() -> changeRightPairForBUYSELL() -> applyChangeToRoom()
     */
    @SuppressLint("CheckResult")
    fun gatherChangeAfterLastLogin() {
        Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    ******************")
        var allSymbol = mCoinList.value!!.map { it.symbol }.toMutableList()
        var isSettingOver = 0 // 3이 되면 deposit, withdraw, order 모두 세팅 끝난것
        var changeList = mutableListOf(listOf<Any>())
        changeList.clear()

        /**
         * 얘가 룸, Asset 에 다 들어있는 symbol 리스트 갖고있는 Observable
         */
        var symbolSettingObservable =
            client.account.snapshot()
                .map { AccountSnapshot ->
                    AccountSnapshot.balances
                        .filter {allSymbolPair.contains(Pair(it.asset,"USDT")) || allSymbolPair.contains(Pair(it.asset,"BTC")) || allSymbolPair.contains(Pair(it.asset, "BNB"))                        }
                        .filter { it.free.toDouble() != 0.00000000 }
                        .filter { it.asset != "USDT" }
                        .map { it.asset }
                        .toMutableList()
                }.map {
                    allSymbol.addAll(it)
                    allSymbol.distinct()
                }.flatMap { symbolList ->
                    Observable.fromIterable(symbolList)
                }



        /**
         * get deposit history & deposit timestamp symbol price
         */
        symbolSettingObservable.flatMap { s ->
            client.withdraw.depositHistory(
                asset = s,
                timestamp = Instant.now().toEpochMilli(),
                startTime = lastCheckTimestamp
            )
        }.flatMap { it ->
            Observable.fromIterable(it.depositList)
        }.flatMap { deposit ->
            var s = deposit.asset
            var p = findPair(s)
            var quantity = deposit.amount.toDouble()
            var interval = CandlestickInterval.MINUTES_1
            var starttime = deposit.insertTime
            var endtime = starttime + 60000
            client.marketData.candlesticks(
                symbol = "$s$p",
                interval = interval,
                startTime = starttime,
                endTime = endtime
            )
                .map { price ->
                    listOf(
                        "DEPOSIT",
                        starttime,
                        s,
                        p,
                        quantity,
                        (price[0].high.toDouble() + price[0].low.toDouble()) / 2.0
                    )
                }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ list ->
                changeList.add(list)
            }, {
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    : deposit error: ${it.message}")
            }, {
                isSettingOver += 1
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    deposit: ${isSettingOver}")
                if (isSettingOver == 3) {
                    changeRightPairForBUYSELL(changeList)
                }
            })


        /**
         * get withdrawal history
         */
        symbolSettingObservable.flatMap { s ->
            client.withdraw.withdrawHistory(
                asset = s,
                timestamp = Instant.now().toEpochMilli(),
                startTime = lastCheckTimestamp
            )
        }.flatMap {
            Observable.fromIterable(it.withdrawList)
                .filter { it.status == WithdrawStatus.COMPLETED }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ withdraw ->
                var s = withdraw.asset
                var timestamp = withdraw.applyTime
                var quantity = withdraw.amount.toDouble()

                changeList.add(listOf("WITHDRAW", timestamp, s, quantity))
            }, {
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    : withdraw error : ${it.message}")
            }, {
                isSettingOver += 1
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    withdrawla: ${isSettingOver}")
                if (isSettingOver == 3) {
                    changeRightPairForBUYSELL(changeList)
                }
            })

        /**
         * get order history
         */
        symbolSettingObservable.flatMap { s ->
            var usdt = client.account.allOrders("${s}USDT").onErrorReturn { Collections.emptyList() }
            var btc = client.account.allOrders("${s}BTC").onErrorReturn { Collections.emptyList() }
            var bnb = client.account.allOrders("${s}BNB").onErrorReturn { Collections.emptyList() }


            Observable.mergeDelayError(usdt,btc,bnb)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                it.filter { it.status==OrderStatus.FILLED || it.status == OrderStatus.PARTIALLY_FILLED  }
                    .filter { it.time > lastCheckTimestamp }
                    .forEach {
                        var timestamp = it.time
                        var s = it.symbol.replace("USDT","").replace("BTC","").replace("BNB","")
                        var p = if(it.symbol.contains("USDT")) "USDT" else if(it.symbol.contains("BTC")) "BTC" else "BNB"
                        var quantity = it.executedQuantity.toDouble()
                        var price = it.price.toDouble()
                        var side = it.side

                        changeList.add(listOf("ORDER",timestamp,side,s,p,quantity,price))
                    }
            }, {
                isSettingOver += 1
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    order throw: ${isSettingOver}. ${it.message}")
                if (isSettingOver == 3) {
                    changeRightPairForBUYSELL(changeList)
                }
            }, {
                isSettingOver += 1
                Log.d("fhrm", "BinanceViewModel -gatherChangeAfterLastLogin(),    order comp: ${isSettingOver}")
                if (isSettingOver == 3) {
                    changeRightPairForBUYSELL(changeList)
                }
            })


    }

    @SuppressLint("CheckResult")
    fun changeRightPairForBUYSELL(changeList:MutableList<List<Any>>){
        Log.d("fhrm", "BinanceViewModel -changeRightPairForBUYSELL(),    : end")
        setLastCheckTimestamp()
        if(changeList.isNullOrEmpty()) return
        if(changeList.any{it[0]=="ORDER"}){ // changeList 에 ORDER 항목이 있을경우
            var tempList =  // CandleStick 으로 가격 확인해서 한번 더 수정해줘야하는 애들
                changeList.filter { it[0].toString() == "ORDER" }
                    .filter { it[2].toString() == "BUY" }
                    .filter { it[4].toString() != findPair(it[3].toString()) || it[6].toString().toDouble() == 0.0} // 정해진 페어보다 하위 페어(ex, USDT 있는데 BTC 로 거래한 내역) 이거나, 시장가 내역으로가격이 0.0인 경우
                    .map { it.toMutableList() }
                    .toMutableList()

            if(!tempList.isNullOrEmpty()){
                changeList.removeAll(tempList)

                client.general.time()
                    .flatMap { time ->
                        Observable.fromIterable(tempList)
                            .flatMap {list ->
                                var s = "${list[3]}"
                                var p = "${list[4]}"
                                var timestamp = list[1].toString().toLong()
                                var price = list[6].toString().toDouble()

                                if(price==0.0){ // 시장가 구매하였을때
                                    if( timestamp+60000>time.serverTime) {
                                        client.marketData.candlesticks(symbol = "$s$p",interval = CandlestickInterval.MINUTES_1,limit = 1)
                                            .map { it ->
                                                var avgPirce = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[6] = avgPirce
                                                list
                                            }
                                    }
                                    else {
                                        client.marketData.candlesticks(symbol = "$s$p",interval = CandlestickInterval.MINUTES_1,startTime = timestamp,endTime = timestamp+60000)
                                            .map { it ->
                                                var avgPirce = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[6] = avgPirce
                                                list
                                            }
                                    }
                                }
                                else {   // 다른 페어로 구매하였을때
                                    client.marketData.candlesticks(symbol = "${p}${findPair(s)}",interval = CandlestickInterval.MINUTES_1,startTime = timestamp,endTime = timestamp+60000)
                                        .map { it ->
                                            var avgPirce = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                            list[4] = findPair(s)
                                            list[6] = list[6].toString().toDouble()*avgPirce
                                            list
                                        }
                                }
                            }
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe ({
                        // 이미 위에 map에서 값 다 변경해놨기 때문에 여기선 할것이 없음.
                    },{
                    },{
                        changeList.addAll(tempList.map { it.toList() })
                        applyChangeToRoom(changeList)
                    })
            }else{ // CandleStick으로 시세 확인할 내역 없음
                applyChangeToRoom(changeList)
            }
        }else{
            applyChangeToRoom(changeList)
        }
    }




    fun applyChangeToRoom(changeList:MutableList<List<Any>>) {

        Log.d("fhrm", "BinanceViewModel -applyChangeToRoom(),    ***********************************")
        var orderedList = changeList.sortedBy { it[1].toString() }

        orderedList.forEach {
            val kind = it[0].toString()
            if(kind=="ORDER") applyOrderToRoom(it)
            else if(kind == "WITHDRAW") applyWithdrawToRoom(it)
            else if(kind == "DEPOSIT") applyDepositToRoom(it)
        }
    }

    private fun applyDepositToRoom(it: List<Any>) {
        Log.d("fhrm", "BinanceViewModel -applyDepositToRoom(),    ***********************************8")
        val s = it[2].toString()
        val p = it[3].toString()
        val quantity = it[4].toString().toDouble()
        val price = it[5].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if(coin==null){ // Room에 없으면 신규이니 그냥 넣으면 됨
            var newCoin = Coin("binance", s, p, quantity, price, quantity * price)
            insertCoinToDB(newCoin)
        }else{
            var newQuantity = coin.quantity + quantity
            var newAvgPrice =
                ((coin.avgPrice * coin.quantity) + (price * quantity)) / (coin.quantity + quantity)
            var newPurchaseAmount = newQuantity * newAvgPrice

            coin.quantity = newQuantity
            coin.avgPrice = newAvgPrice
            coin.purchaseAmount = newPurchaseAmount

            insertCoinToDB(coin)
        }



    }

    private fun applyWithdrawToRoom(it: List<Any>) {
        Log.d("fhrm", "BinanceViewModel -applyWithdrawToRoom(),    *****************************************8")
        val s = it[2].toString()
        val quantity = it[3].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if(coin==null){} // Room에 없으면 그냥 넘기기
        else{
            coin.quantity -= quantity
            insertCoinToDB(coin)
        }

    }

    private fun applyOrderToRoom(it: List<Any>) {
        Log.d("fhrm", "BinanceViewModel -applyOrderToRoom(),    ****************************************")
        val side = it[2].toString()
        val s = it[3].toString()
        val p = it[4].toString()
        var quantity = it[5].toString().toDouble()
        var price = it[6].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if(side=="BUY") { // 구매
            if (coin == null) { // 기존 목록에 없다면 새로 추가
                var newCoin = Coin("binance", s, p, quantity, price, quantity * price)
                insertCoinToDB(newCoin)
            } else { // 기존 목록에 있다면
                var newQuantity = coin.quantity + quantity
                var newAvgPrice =
                    ((coin.avgPrice * coin.quantity) + (price * quantity)) / (coin.quantity + quantity)
                var newPurchaseAmount = newQuantity * newAvgPrice

                coin.quantity = newQuantity
                coin.avgPrice = newAvgPrice
                coin.purchaseAmount = newPurchaseAmount

                insertCoinToDB(coin)
            }
        }else{ //판매
            if(coin==null){} //Room에 없으면 그냥 넘기기
            else{
                coin.quantity -= quantity
                insertCoinToDB(coin)
            }
        }
    }


    fun findPair(s: String): String {

        if (allSymbolPair.contains(Pair(s, "USDT"))) return "USDT"
        else if (allSymbolPair.contains(Pair(s, "BTC"))) return "BTC"
        else return "BNB"
    }

    @SuppressLint("CheckResult")
    fun getOrderHistory(inputSymbol: String) {
        client.account.allOrders(symbol = inputSymbol) // sp
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ it ->
                it.forEachIndexed { index, order ->
                    Log.d("fhrm", "BinanceViewModel -getOrderHistory(),    order: ${order}")
                }
            }, {
                Log.d("fhrm", "BinanceViewModel -getOrderHistory(),    : error")
            }
                , {
                    Log.d("fhrm", "BinanceViewModel -getOrderHistory(),    : com")
                })
    }


    /**
     * timeStamp 시간대에 해당 symbol 의 평균가격을 구해줌
     */
    @SuppressLint("CheckResult")
    fun getSpecificTimeAvgPrice(symbol: String, timestamp: Long) {
        // 특정시간 평균 가격 구하는 코드
        client.marketData.candlesticks(
            symbol = symbol,    //sp
            interval = CandlestickInterval.MINUTES_1,
            limit = 1
//            startTime = timestamp,
//            endTime = timestamp + 60000
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
                        String.format("%.2f", ((currentPrice / coin.avgPrice) - 1) * 100)
                            .toDouble()
                    var profilt = String.format("%.6f",coin.purchaseAmount * (percent / 100) ).toDouble()

                    coin.percent = percent
                    coin.profit = profilt

                    insertCoinToDB(coin)
                }, { e ->
                    Log.d("fhrm", "BinanceViewModel -test(),    error: ${e.message}")
                })
        }
    }

    fun test() {
        var a = mCoinList.value!![0]
        a.percent=123.123
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
            Log.d("fhrm", "BinanceViewModel -insertCoinToDB(),    ****************************************")
            mCoinRepository.insert(coin)
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
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getCurrentTime(),    it.serverTime: ${it.serverTime}"
                )
                Log.d("fhrm", "BinanceViewModel -getCurrentTime(),    Instant.now().toEpochMilli(): ${Instant.now().toEpochMilli()}")
            }
    }


    @SuppressLint("CheckResult")
    fun getAllSymbolPair() {
        client.general.exchangeInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ it ->
                it.symbols.forEachIndexed { index, symbol ->
                    if (symbol.quoteAsset == "USDT" || symbol.quoteAsset == "BTC" || symbol.quoteAsset == "BNB") // USDT, BTC, BNB 페어만 가져
                        allSymbolPair.add(Pair(symbol.baseAsset, symbol.quoteAsset))
                }
            }, {
                Log.d("fhrm", "BinanceViewModel -getAllSymbolPair(),    error: ${it.message}")
            }, {
                isAllSymbolPairSetting.value = true
            })
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