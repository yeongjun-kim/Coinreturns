package com.yj.coinreturns.view

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import com.yj.coinreturns.R
import com.yj.coinreturns.databinding.ActivityLoginBinding
import com.yj.coinreturns.databinding.DialogLoginApiBinding
import com.yj.coinreturns.model.App
import com.yj.coinreturns.viewModel.LoginViewModel
import kotlinx.android.synthetic.main.activity_login.*



class LoginActivity : AppCompatActivity() {

    lateinit var binding: ActivityLoginBinding
    lateinit var loginViewModel: LoginViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_login)
        loginViewModel = ViewModelProvider(
            this,
            LoginViewModel.Factory(application)
        ).get(LoginViewModel::class.java)


        binding.apply {
            lifecycleOwner = this@LoginActivity
            av = this@LoginActivity
        }


        /**
         *  Default -1, Success 1, Show PopupDialog 0
         */
        loginViewModel.isBinanceLoginSuccess.observe(this, Observer {   isBinanceLoginSuccess->
            if(isBinanceLoginSuccess ==1 ){
                startActivity(Intent(this,BinanceActivity::class.java))
            }else if(isBinanceLoginSuccess ==0){
                openInputAPIkeyDialog("binance")
            }
        })

        loginViewModel.isHuobiLoginSuccess.observe(this, Observer { isHuobiLoginSuccess ->
            if(isHuobiLoginSuccess == 1){
                // TODO()
            }else if (isHuobiLoginSuccess == 0){
                openInputAPIkeyDialog("huobi")
            }
        })

        loginViewModel.isCoinbaseLoginSuccess.observe(this, Observer { isCoinbaseLoginSuccess ->
            if(isCoinbaseLoginSuccess == 1){
                // TODO()
            }else if (isCoinbaseLoginSuccess == 0){
                openInputAPIkeyDialog("coinbase")
            }
        })



        initStatusBar()






        login_btn_test.setOnClickListener {
            startActivity(Intent(this,BinanceActivity::class.java))

        }



    }



    fun loginBinance() {
        loginViewModel.checkBinanceKey()
    }

    fun loginHuobi() {
        loginViewModel.checkHuobiKey()
    }

    fun loginCoinbase() {
        loginViewModel.checkCoinbaseKey()
    }


    fun openInputAPIkeyDialog(exchange: String) {
        Toast.makeText(this, "Wrong API / Secret Key. Please check the key.", Toast.LENGTH_LONG)
            .show()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_login_api, null, false)
        val binding = DialogLoginApiBinding.bind(view)
        binding.apply {
            av = this@LoginActivity
            lvm = loginViewModel
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("INPUT YOUR API & SECRET KEY")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                if(exchange == "binance"){
                    loginViewModel.setBinanceKey()
                }else if(exchange == "huobi"){
                    loginViewModel.setHuobiKey()
                }
                else if(exchange == "coinbase"){
                    loginViewModel.setCoinbaseKey()
                }
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("fhrm", "LoginActivity -onDestroy(),    ")
    }

    private fun initStatusBar() {
        window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = Color.TRANSPARENT
    }
}