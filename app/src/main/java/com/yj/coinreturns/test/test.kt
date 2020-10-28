package com.yj.coinreturns.test

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
import com.yj.coinreturns.viewModel.BinanceViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*

class test(application: Application) : AndroidViewModel(application) {


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
//        lastCheckTimestamp = 1553802172110
    }

    fun getAllFromRoom() = mCoinList
    fun getLastCheckTimestamp() = lastCheckTimestamp
    fun getIsAllSymbolPairSetting() = isAllSymbolPairSetting


    /**
     * getHaveToCheckSymbol() -> gatherChangeAfterLastLogin() -> changeRightPairForBUYSELL() -> applyChangeToRoom()
     */
    @SuppressLint("CheckResult")
    fun getHaveToCheckSymbol() {
        Log.d("fhrm", "BinanceViewModel -getHaveToCheckSymbol(),    : here")
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
        var changeList = mutableListOf(listOf<Any>())
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
                        listOf(
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
                changeList.add(listOf("WITHDRAW", it.applyTime, it.asset, it.amount.toDouble()))
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
                    lateinit var s:String
                    lateinit var p :String

                    if(it.symbol == "BNBBTC"){
                        s = "BNB"
                        p = "BTC"
                    }else if(it.symbol =="BTCUSDT"){
                        s = "BTC"
                        p = "USDT"
                    }else if(it.symbol == "BNBUSDT"){
                        s = "BNB"
                        p = "USDT"
                    }else {
                        s = it.symbol.replace("USDT", "").replace("BTC", "").replace("BNB", "")
                        p = if (it.symbol.contains("USDT")) "USDT" else if (it.symbol.contains("BTC")) "BTC" else "BNB"

                    }

                    changeList.add(
                        listOf(
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
                changeRightPairForBUYSELL(changeList)
            })


    }

    fun test(s: String, p: String) {
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                var a = mutableListOf(listOf("ORDER", it.serverTime, "SELL", s, p, 50.0, 0.0000184))
                changeRightPairForBUYSELL(a)
            }

    }

    @SuppressLint("CheckResult")
    fun changeRightPairForBUYSELL(changeList: MutableList<List<Any>>) {
        if (changeList.isNullOrEmpty()) {
            setLastCheckTimestamp()
            return
        }
        if (changeList.any { it[0] == "ORDER" }) { // changeList 에 ORDER 항목이 있을경우
            var tempList =  // CandleStick 으로 가격 확인해서 한번 더 수정해줘야하는 애들
                changeList.filter { it[0].toString() == "ORDER" }
                    .filter { it[2].toString() == "BUY" }
                    .filter { it[4].toString() != findPair(it[3].toString()) || it[6].toString().toDouble() == 0.0 } // 정해진 페어보다 하위 페어(ex, USDT 있는데 BTC 로 거래한 내역) 이거나, 시장가 내역으로가격이 0.0인 경우
                    .map { it.toMutableList() }
                    .toMutableList()

            var sellList =
                changeList.filter { it[0].toString() == "ORDER" }
                    .filter { it[2].toString() == "SELL" } //생각해보니까 BUY 뿐만 아니라 SELL 에서 시장가로 판것도 해당 시간대 가격으로 수정해줘야함 ( BTC,BNB 페어로 팔았을때 새로 추가해줘야하기때문에 )
                    .filter { it[6].toString().toDouble() == 0.0 }
                    .map { it.toMutableList() }
                    .toMutableList()

            tempList.addAll(sellList)


            if (!tempList.isNullOrEmpty()) {
                changeList.removeAll(tempList)
                client.general.time()
                    .flatMap { time ->
                        Observable.fromIterable(tempList)
                            .flatMap { list ->
                                var s = "${list[3]}"
                                var p = "${list[4]}"
                                var timestamp = list[1].toString().toLong()
                                var price = list[6].toString().toDouble()

                                if (price == 0.0) { // 시장가 구매하였을때
                                    if (timestamp + 60000 > time.serverTime) {
                                        client.marketData.candlesticks(
                                            symbol = "$s$p",
                                            interval = CandlestickInterval.MINUTES_1,
                                            limit = 1
                                        )
                                            .map { it ->
                                                var avgPirce =
                                                    (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[6] = avgPirce
                                                list
                                            }
                                    } else {
                                        client.marketData.candlesticks(
                                            symbol = "$s$p",
                                            interval = CandlestickInterval.MINUTES_1,
                                            startTime = timestamp,
                                            endTime = timestamp + 60000
                                        )
                                            .map { it ->
                                                var avgPirce =
                                                    (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[6] = avgPirce
                                                list
                                            }
                                    }
                                } else {   // 다른 페어로 구매하였을때
                                    if (timestamp + 60000 > time.serverTime) {
                                        client.marketData.candlesticks(
                                            symbol = "${p}${findPair(s)}",
                                            interval = CandlestickInterval.MINUTES_1,
                                            limit = 1
                                        )
                                            .map { it ->
                                                var avgPirce =
                                                    (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[4] = findPair(s)
                                                list[6] = list[6].toString().toDouble() * avgPirce
                                                list
                                            }
                                    }else{
                                        client.marketData.candlesticks(
                                            symbol = "${p}${findPair(s)}",
                                            interval = CandlestickInterval.MINUTES_1,
                                            startTime = timestamp,
                                            endTime = timestamp + 60000
                                        )
                                            .map { it ->
                                                var avgPirce =
                                                    (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                                list[4] = findPair(s)
                                                list[6] = list[6].toString().toDouble() * avgPirce
                                                list
                                            }
                                    }
                                }
                            }
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        // 이미 위에 map에서 값 다 변경해놨기 때문에 여기선 할것이 없음.
                    }, {
                    }, {
                        changeList.addAll(tempList.map { it.toList() })
                        addSellToBuy(changeList)

                    })
            } else { // CandleStick으로 시세 확인할 내역 없음
                applyChangeToRoom(changeList)
            }
        } else {
            applyChangeToRoom(changeList)
        }
    }

    /**
     * ORDER, SELL 중에
     * BTC, BNB 페어로 판매 했으면
     * 해당 BTC, BNB 도 새로 생기는거니까(BUY처럼)
     * 해당 내역도 list에 추가해주는 메소드
     */
    @SuppressLint("CheckResult")
    fun addSellToBuy(changeList: MutableList<List<Any>>) {
        changeList.forEach {
            Log.d("fhrm", "addSellToBuy before : ${it}")
        }

        var sellList =
            changeList.filter { it[0].toString() == "ORDER" }
                .filter { it[2].toString() == "SELL" }
                .filter { it[4].toString() != "USDT" }
                .map { it.toMutableList() }

        sellList.forEach {
            it[2] = "BUY"
            it[3] = it[4]
            it[4] = "USDT"
            it[5] = it[5].toString().toDouble() * it[6].toString().toDouble()
            it[6] = 0.0
        }

        sellList.forEachIndexed { index, list ->
            Log.d("fhrm", "sellList    index: ${index}, list: ${list}")
        }


        if (!sellList.isNullOrEmpty()) {
            client.general.time()
                .flatMap { time ->
                    Observable.fromIterable(sellList)
                        .flatMap { list ->
                            var s = "${list[3]}"
                            var p = "${list[4]}"
                            var timestamp = list[1].toString().toLong()
                            if (timestamp + 60000 > time.serverTime) {
                                client.marketData.candlesticks(
                                    symbol = "$s$p",
                                    interval = CandlestickInterval.MINUTES_1,
                                    limit = 1
                                )
                                    .map { it ->
                                        var avgPirce =
                                            (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                        list[6] = avgPirce
                                        list
                                    }
                            } else {
                                client.marketData.candlesticks(
                                    symbol = "$s$p",
                                    interval = CandlestickInterval.MINUTES_1,
                                    startTime = timestamp,
                                    endTime = timestamp + 60000
                                )
                                    .map { it ->
                                        var avgPirce =
                                            (it[0].high.toDouble() + it[0].low.toDouble()) / 2.0
                                        list[6] = avgPirce
                                        list
                                    }
                            }
                        }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                }, {
                    changeList.addAll(sellList.map { it.toList() })
                    Log.d("fhrm", "BinanceViewModel -addSellToBuy(),    : 1")
                    applyChangeToRoom(changeList)
                })
        }
        Log.d("fhrm", "BinanceViewModel -addSellToBuy(),    : 2")
        applyChangeToRoom(changeList)
    }


    fun applyChangeToRoom(changeList: MutableList<List<Any>>) {

        changeList.forEachIndexed { index, list ->
            Log.d("fhrm", "apply change -> index: ${index}, list: ${list}")
        }

        setLastCheckTimestamp()
        var orderedList = changeList.sortedBy { it[1].toString() }.toMutableList()


        orderedList.forEach {
            val kind = it[0].toString()
            if (kind == "ORDER") applyOrderToRoom(it)
            else if (kind == "WITHDRAW") applyWithdrawToRoom(it)
            else if (kind == "DEPOSIT") applyDepositToRoom(it)
        }
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


    fun testDeposit() {
        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                var test = mutableListOf(listOf<Any>())
                test.clear()
                var a = listOf("DEPOSIT", it.serverTime, "EOS", "BTC", 1, 2, 3)
                test.add(a)
                applyChangeToRoom(test)
            }

    }

    fun testWithdraw() {
//        changeList.add(listOf("WITHDRAW",it.applyTime,it.asset,it.amount.toDouble()))

        client.general.time()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                var test = mutableListOf(listOf<Any>())
                test.clear()
                var a = listOf("WITHDRAW", it.serverTime, "XRP", 100)
                test.add(a)
                applyChangeToRoom(test)
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