package com.pb.lifecycleawarecallback

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() , NetworkConnectivityObserver.NetworkConnectivityChangeListener {

    companion object {
        val TAG = "MainActivity"
    }
    private lateinit var networkConnectivityObserver: NetworkConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        networkConnectivityObserver=NetworkConnectivityObserver.init(this)
    }

    override fun onInternetConnectivityChanged(internetAvailable: Boolean) {
        Log.d(TAG,"Network Status = $internetAvailable")
    }
}
