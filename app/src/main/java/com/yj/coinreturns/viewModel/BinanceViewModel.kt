package com.yj.coinreturns.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.cluttered.cryptocurrency.BinanceClient
import com.cluttered.cryptocurrency.model.account.OrderStatus
import com.cluttered.cryptocurrency.model.marketdata.CandlestickInterval
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
     * getHaveToCheckSymbol() -> gatherChangeAfterLastLogin() -> changeRightPairForBUYSELL() -> applyChangeToRoom()
     */
    @SuppressLint("CheckResult")
    fun getHaveToCheckSymbol() {
        var allSymbol = mCoinList.value!!.map { it.symbol }.toMutableList()

        /**
         * 얘가 룸, Asset 에 다 들어있는 symbol 리스트 갖고있는 Observable
         */
        client.account.snapshot()
            .map { AccountSnapshot ->
                AccountSnapshot.balances
                    .filter {
                        allSymbolPair.contains(
                            Pair(
                                it.asset,
                                "USDT"
                            )
                        ) || allSymbolPair.contains(
                            Pair(
                                it.asset,
                                "BTC"
                            )
                        ) || allSymbolPair.contains(Pair(it.asset, "BNB"))
                    }
                    .filter { it.free.toDouble() != 0.00000000 }
                    .filter { it.asset != "USDT" }
                    .map { it.asset }
                    .toMutableList()
            }.map {
                allSymbol.addAll(it)
                allSymbol.distinct()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ list ->
                gatherChangeAfterLastLogin(list)
            }, {
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getHaveToCheckSymbol(),    error: ${it.message}"
                )
            })
    }


    @SuppressLint("CheckResult")
    fun gatherChangeAfterLastLogin(sList: List<String>) {
        var changeList = mutableListOf(mutableListOf<Any>())
        changeList.clear()

        client.withdraw.depositHistory(timestamp = Instant.now().toEpochMilli()) // Check Deposit
            .flatMapIterable { it.depositList }
            .filter { it.insertTime >= lastCheckTimestamp } // status는 체크할필요없음 ( PENDING, SUCCESS 둘다 어쨋든 들어오는거고, 펜딩과 성공의 inserttime이 변경되지 않아서. )
            .flatMap { deposit ->
                client.marketData.candlesticks(
                    symbol = "${deposit.asset}${findPair(deposit.asset)}",
                    interval = CandlestickInterval.MINUTES_1,
                    limit = 1,
                    startTime = deposit.insertTime - 60000,
                    endTime = deposit.insertTime
                )
                    .map { price ->
                        mutableListOf(
                            "DEPOSIT",
                            deposit.insertTime,
                            deposit.asset,
                            findPair(deposit.asset),
                            deposit.amount.toDouble(),
                            (price[0].high.toDouble() + price[0].low.toDouble()) / 2.0
                        )
                    }
            }
            .map {
                changeList.add(it)
            }
            .toList()
            .toObservable()
            .flatMap {
                // Check Withdraw
                client.withdraw.withdrawHistory(timestamp = Instant.now().toEpochMilli()) // Deposit 과 동
            }
            .flatMapIterable { it.withdrawList }
            .filter { it.applyTime >= lastCheckTimestamp } // Deposit 과 동일
            .map {
                changeList.add(
                    mutableListOf(
                        "WITHDRAW",
                        it.applyTime,
                        it.asset,
                        it.amount.toDouble()
                    )
                )
            }
            .toList()
            .toObservable()
            .flatMapIterable { sList } // USDT, BTC, BNB 페어에 대한 order history 만 체크함
            .flatMap { s ->
                // Check Order
                var usdt =
                    client.account.allOrders("${s}USDT").onErrorReturn { Collections.emptyList() }
                var btc =
                    client.account.allOrders("${s}BTC").onErrorReturn { Collections.emptyList() }
                var bnb =
                    client.account.allOrders("${s}BNB").onErrorReturn { Collections.emptyList() }

                Observable.mergeDelayError(usdt, btc, bnb)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ inputList ->
                var orderList =
                    inputList.filter { it.status == OrderStatus.FILLED || it.status == OrderStatus.PARTIALLY_FILLED }
                orderList = orderList.filter { it.time > lastCheckTimestamp }

                orderList.forEach {
                    lateinit var s: String
                    lateinit var p: String

                    if (it.symbol == "BNBBTC") {
                        s = "BNB"
                        p = "BTC"
                    } else if (it.symbol == "BTCUSDT") {
                        s = "BTC"
                        p = "USDT"
                    } else if (it.symbol == "BNBUSDT") {
                        s = "BNB"
                        p = "USDT"
                    } else {
                        s = it.symbol.replace("USDT", "").replace("BTC", "").replace("BNB", "")
                        p =
                            if (it.symbol.contains("USDT")) "USDT" else if (it.symbol.contains("BTC")) "BTC" else "BNB"

                    }

                    changeList.add(
                        mutableListOf(
                            "ORDER",
                            it.time,
                            it.side,
                            s,
                            p,
                            it.executedQuantity.toDouble(),
                            it.price.toDouble()
                        )
                    )
                }
            }, {
                Log.d("fhrm", "BinanceViewModel -getDeposit(),    error: ${it.message}")
            }, {
                changeList.forEachIndexed { index, list ->
                    Log.d("fhrm", "catch -> index: ${index}, list: ${list}")
                }
                changeMarketPriceForOriginal(changeList)
            })


    }


    @SuppressLint("CheckResult")
    fun changeMarketPriceForOriginal(changeList: MutableList<MutableList<Any>>) {
        changeList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "1    index: ${index}, list: ${mutableList}")
        }
        var marketPriceList =
            changeList.filter { it[0] == "ORDER" }
                .filter { it[6] == 0.0 }

        if (marketPriceList.isNullOrEmpty()) { // 시장가로 구매한 내역 없으니 다음으로 넘어감
            makeOppositeSideList(changeList)
        } else { // 시장가로 구매한 내역 있으니 평단 수정해주기
            client.general.time()
                .flatMap { currentTime ->
                    Observable.fromIterable(marketPriceList)
                        .flatMap { list ->
                            var s = "${list[3]}"
                            var p = "${list[4]}"
                            var timestamp = list[1].toString().toLong()
                            var observable =
                                if (timestamp + 60000 > currentTime.serverTime)
                                    client.marketData.candlesticks(
                                        symbol = "$s$p",
                                        interval = CandlestickInterval.MINUTES_1,
                                        limit = 1
                                    )
                                else
                                    client.marketData.candlesticks(
                                        symbol = "$s$p",
                                        interval = CandlestickInterval.MINUTES_1,
                                        startTime = timestamp,
                                        endTime = timestamp + 60000
                                    )

                            observable.map {
                                list[6] = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                list
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, {
                    Log.d("fhrm", "changeMarketPriceForOriginal(),    error: ${it.message}")
                }, {
                    makeOppositeSideList(changeList)
                })
        }

    }

    fun makeOppositeSideList(changeList: MutableList<MutableList<Any>>) {
        changeList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "2    index: ${index}, list: ${mutableList}")
        }
        var orderList =
            changeList.filter { it[0] == "ORDER" }.toMutableList()

        if (orderList.isNullOrEmpty()) { // ORDER가 없으면 반대 side 쌍 만들어줄 필요 없으니 바로 다음 step으로
            changeMarketPriceForNew(changeList)
        } else { // BUY/SELL 내역이 있으니, 그의 반대도 리스트에 추가해주기( 예, buy -> sell )
            var newList = mutableListOf(mutableListOf<Any>())
            newList.clear()

            orderList.forEach {
                var new = mutableListOf(
                    "ORDER",
                    it[1],
                    if (it[2].toString() == "BUY") "SELL" else "BUY",
                    it[4],
                    findPair(it[4].toString()),
                    it[5].toString().toDouble() * it[6].toString().toDouble(),
                    0.0
                )
                newList.add(new)
            }

            changeList.addAll(newList.filter { it[3].toString() != "USDT" })
            changeMarketPriceForNew(changeList)
        }

    }

    @SuppressLint("CheckResult")
    fun changeMarketPriceForNew(changeList: MutableList<MutableList<Any>>) {
        changeList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "3    index: ${index}, list: ${mutableList}")
        }
        var marketPriceList =
            changeList.filter { it[0] == "ORDER" }
                .filter { it[6] == 0.0 }

        if (marketPriceList.isNullOrEmpty()) { // 새로 추가된 order 리스트가 없을시 바로 다음단계
            chagePair(changeList)
        } else { // 새로 추가된 order 리스트가 존재한다면 해당 시간대에 시세로 평단 변경
            client.general.time()
                .flatMap { currentTime ->
                    Observable.fromIterable(marketPriceList)
                        .flatMap { list ->
                            var s = "${list[3]}"
                            var p = "${list[4]}"
                            var timestamp = list[1].toString().toLong()
                            var observable =
                                if (timestamp + 60000 > currentTime.serverTime)
                                    client.marketData.candlesticks(
                                        symbol = "$s$p",
                                        interval = CandlestickInterval.MINUTES_1,
                                        limit = 1
                                    )
                                else
                                    client.marketData.candlesticks(
                                        symbol = "$s$p",
                                        interval = CandlestickInterval.MINUTES_1,
                                        startTime = timestamp,
                                        endTime = timestamp + 60000
                                    )

                            observable.map {
                                list[6] = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                list
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, {
                    Log.d("fhrm", "changeMarketPriceForOriginal(),    error: ${it.message}")
                }, {
                    chagePair(changeList)
                })
        }
    }

    @SuppressLint("CheckResult")
    fun chagePair(changeList: MutableList<MutableList<Any>>) {
        changeList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "4    index: ${index}, list: ${mutableList}")
        }
        var haveToChangeList =
            changeList.filter { it[0] == "ORDER" }
//                .filter { it[2] == "BUY" }
                .filter { it[4] != findPair(it[3].toString()) }

        if(haveToChangeList.isNullOrEmpty()){ // 페어 바꿔줄것이 없으면 다음단계로
            applyChangeToRoom(changeList)
        }else{ // 페어 바꿔줄것이 있으면
            client.general.time()
                .flatMap { currentTime ->
                    Observable.fromIterable(haveToChangeList)
                        .flatMap { list ->
                            var s = "${list[3]}"
                            var p = "${list[4]}"
                            var timestamp = list[1].toString().toLong()
                            var observable =
                                if (timestamp + 60000 > currentTime.serverTime)
                                    client.marketData.candlesticks(
                                        symbol = "$s${findPair(s)}",
                                        interval = CandlestickInterval.MINUTES_1,
                                        limit = 1
                                    )
                                else
                                    client.marketData.candlesticks(
                                        symbol = "$s${findPair(s)}",
                                        interval = CandlestickInterval.MINUTES_1,
                                        startTime = timestamp,
                                        endTime = timestamp + 60000
                                    )

                            observable.map {
                                list[4] = findPair(s)
                                list[6] = (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                list
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, {
                    Log.d("fhrm", "changeMarketPriceForOriginal(),    error: ${it.message}")
                }, {
                    applyChangeToRoom(changeList)
                })

        }

    }


    @SuppressLint("CheckResult")
    fun test1() {
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                var changeList: MutableList<MutableList<Any>> = mutableListOf(mutableListOf("ORDER",it.serverTime,"BUY","XRP","AUD",100.0,0.0))
                changeMarketPriceForOriginal(changeList)
            }
    }

    @SuppressLint("CheckResult")
    fun test2(){
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                var changeList: MutableList<MutableList<Any>> =
                    mutableListOf(
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
//                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0),
                        mutableListOf("ORDER",1603990161166,"SELL","XRP","AUD",50.0,0.0),
                        mutableListOf("ORDER",it.serverTime,"SELL","XRP","AUD",50.0,0.0)
                    )
                changeMarketPriceForOriginal(changeList)
            }
    }

    fun test3(s:String){
        Log.d("fhrm", "BinanceViewModel -test3(),    findPair($s): ${findPair(s)}")
    }

    fun test4(){
        client.general.exchangeInfo()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                it.rateLimits.forEach {
                    Log.d("fhrm", "BinanceViewModel -test4(),    : ${it}")
                }
            }
    }


    fun applyChangeToRoom(changeList: MutableList<MutableList<Any>>) {
        changeList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "5    index: ${index}, list: ${mutableList}")
        }
        setLastCheckTimestamp()
        var orderedList = changeList.sortedBy { it[1].toString() }.toMutableList()

        orderedList.forEachIndexed { index, mutableList ->
            Log.d("fhrm", "apply list    index: ${index}, list: ${mutableList}")
        }





        orderedList.forEach {
            val kind = it[0].toString()
            if (kind == "ORDER") applyOrderToRoom(it)
            else if (kind == "WITHDRAW") applyWithdrawToRoom(it)
            else if (kind == "DEPOSIT") applyDepositToRoom(it)
        }

        /****************************************************************************************************************
         ****************************************************************************************************************
         ****************************************************************************************************************
         ****************************************************************************************************************
         *
         * 여기서 위에 orderList.forEach 이거 한번에 묶어서 insertCoinToDB 이거 날려줘야할듯.
         * 심볼당 1개로만 정리해서.
         *
         ****************************************************************************************************************
         ****************************************************************************************************************
         ****************************************************************************************************************
         ****************************************************************************************************************/
    }

    private fun applyOrderToRoom(it: List<Any>) {
        val side = it[2].toString()
        val s = it[3].toString()
        val p = it[4].toString()
        var quantity = it[5].toString().toDouble()
        var price = it[6].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if (side == "BUY") { // 구매
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
        } else { //판매
            if (coin == null) {
            } //Room에 없으면 그냥 넘기기
            else {
                coin.purchaseAmount -= quantity*price
                coin.quantity -= quantity
                insertCoinToDB(coin)
            }
        }

    }

    private fun applyDepositToRoom(it: List<Any>) {

        val s = it[2].toString()
        val p = it[3].toString()
        val quantity = it[4].toString().toDouble()
        val price = it[5].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if (coin == null) { // Room에 없으면 신규이니 그냥 넣으면 됨
            var newCoin = Coin("binance", s, p, quantity, price, quantity * price)
            insertCoinToDB(newCoin)
        } else {
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

        val s = it[2].toString()
        val quantity = it[3].toString().toDouble()
        var coin = mCoinList.value!!.find { it.symbol == s }

        if (coin == null) {
        } // Room에 없으면 그냥 넘기기
        else {
            coin.quantity -= quantity
            insertCoinToDB(coin)
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
                    var profilt =
                        String.format("%.6f", coin.purchaseAmount * (percent / 100)).toDouble()

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
                Log.d(
                    "fhrm",
                    "BinanceViewModel -getCurrentTime(),    Instant.now().toEpochMilli(): ${Instant.now().toEpochMilli()}"
                )
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