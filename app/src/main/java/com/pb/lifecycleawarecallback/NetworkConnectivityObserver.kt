package com.pb.lifecycleawarecallback

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class NetworkConnectivityObserver private constructor(private val activity: AppCompatActivity)
    : LifecycleObserver {

    private var changeListener: NetworkConnectivityChangeListener? = null
    private val workerHandler: Handler
    private val uiHandler: Handler
    /**
     * Return last stored status of internet connectivity check
     *
     * @return true if connected, false otherwise
     */
    var isInternetAvailable: Boolean = false
        private set

    /**
     * To check and compare internet current fetched state with this previous state.
     * If state is same, prevent unwanted call of `NetworkConnectivityChangeListener.onInternetConnectivityChanged()`.
     */
    private var internetPreviousState: Boolean = false
    private var shouldCompareInternetState: Boolean = false
    private  lateinit var siteURL: URL

    init {
        uiHandler = Handler(Looper.getMainLooper())
        val handlerThread = HandlerThread(HANDLER_THREAD_NAME)
        handlerThread.start()
        try {
            siteURL = URL(SOCKET_HOST_URL)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        }

        workerHandler = Handler(handlerThread.looper)
        isInternetAvailable =
            isNetworkConnected(activity) //Initialization time, check at least connection status.
        internetPreviousState = isInternetAvailable
        activity.registerReceiver(NetworkChangeReceiver(), IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        activity.lifecycle.addObserver(this)
    }

    /**
     * Allow internet to be compared with previous state to prevent unwanted call of `NetworkConnectivityChangeListener.onInternetConnectivityChanged()`.
     */
    fun setCompareInternetWithPreviousState(shouldCompare: Boolean?) {
        shouldCompareInternetState = shouldCompare!!
    }

    /**
     * Register Network connectivity change listener and trigger network checking now
     *
     * @param networkConnectivityChangeListener callback listener
     */
    @Synchronized
    fun registerCallbackListener(networkConnectivityChangeListener: NetworkConnectivityChangeListener) {
        registerCallbackListener(
            networkConnectivityChangeListener,
            true
        ) //true for legacy implementation, otherwise false would be great.
    }

    /**
     * Register Network connectivity change listener, manage request checking based on parameter requestCheckNow value.
     *
     * @param networkConnectivityChangeListener callback listener
     * @param requestCheckNow                   flag true to enforce checking now, false to register callback just for future state changes.
     */
    @Synchronized
    fun registerCallbackListener(
        networkConnectivityChangeListener: NetworkConnectivityChangeListener,
        requestCheckNow: Boolean
    ) {
        changeListener = networkConnectivityChangeListener
        if (requestCheckNow) {
            checkInternetConnectivityNow(changeListener)
        }
    }

    /**
     * Unregister Network connectivity change listener
     *
     * @param networkConnectivityChangeListener callback listener
     */
    @Synchronized
    fun unregisterCallbackListener(networkConnectivityChangeListener: NetworkConnectivityChangeListener) {
        changeListener = null
    }

    /**
     * Request connectivity check now, specifically for given parameter callback listener only.
     *
     * @param networkConnectivityChangeListener callback listener
     */
    fun requestCheckInternetConnectivityNow(networkConnectivityChangeListener: NetworkConnectivityChangeListener) {
        checkInternetConnectivityNow(networkConnectivityChangeListener)
    }

    private fun checkHostAvailable(callback: NetworkConnectivityChangeListener?) {
        workerHandler.post {
            /*    try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(SOCKET_HOST_URL, SOCKET_HOST_PORT), CONNECTION_TIME_OUT);
                    log.d("checkHostAvailable : Connection Available: " + socket.isConnected());
                    postCallbackUpdates(callbacks, socket.isConnected());
                    try {
                        socket.close(); //clean up
                    } catch (Throwable ignore) {
                    }
                } catch (IOException e) {
                    // Either we have a timeout or unreachable host or failed DNS lookup
                    //noinspection ConstantConditions
                    log.d("checkHostAvailable : Connection Not Available, Reason: " + (e == null ? "Unknown" : e.getMessage()));
                    postCallbackUpdates(callbacks, false);
                }
*/
            try {
                val connection = siteURL.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECTION_TIME_OUT
                connection.readTimeout = READ_TIME_OUT
                connection.connect()
                val code = connection.responseCode
                if (code >= 200 && code < 300) {
                    Log.d(TAG, "connection available")
                    postCallbackUpdates(callback, true)
                } else {
                    Log.d(TAG, "connection not available-" + connection.responseCode)
                    postCallbackUpdates(callback, false)
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Either we have a timeout or unreachable host or failed DNS lookup
                Log.d(TAG, "checkHostAvailable : Connection Not Available, Reason: " + e.message)
                postCallbackUpdates(callback, false)
            }
        }
    }

    @Synchronized
    private fun postCallbackUpdates(callback: NetworkConnectivityChangeListener?, connectivityAvailable: Boolean) {
        isInternetAvailable = connectivityAvailable
        if (callback != null) {
            uiHandler.post {
                try {
                    if (shouldCompareInternetState) {
                        if (connectivityAvailable != internetPreviousState)
                            callback.onInternetConnectivityChanged(connectivityAvailable)
                    } else {
                        callback.onInternetConnectivityChanged(connectivityAvailable)
                    }
                } catch (ignore: Exception) {
                }

                internetPreviousState = isInternetAvailable
            }
        } else {
            internetPreviousState = isInternetAvailable
        }
    }

    private inner class NetworkChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(ConnectivityManager.CONNECTIVITY_ACTION, intent.action)) {
                checkInternetConnectivityNow(changeListener)
            }
        }
    }

    /**
     * Forcefully checking internet connectivity, callback will be given to registered listener.
     */
    private fun checkInternetConnectivityNow(callback: NetworkConnectivityChangeListener?) {
        if (isNetworkConnected(activity)) {
            checkHostAvailable(callback)
        } else {
            postCallbackUpdates(callback, false)
        }
    }

    /**
     * Forcefully checking internet connectivity manually when user wants to retry again, callback will be given to registered listener.
     */
    fun checkInternetConnectivityManually() {
        checkInternetConnectivityNow(changeListener)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun registerListener(){
        changeListener=activity as NetworkConnectivityChangeListener
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun unregisterListener(){
        changeListener=null
    }

    interface NetworkConnectivityChangeListener {
        fun onInternetConnectivityChanged(internetAvailable: Boolean)
    }

    companion object {

        private val TAG = "NetworkObserver"
        private lateinit var instance: NetworkConnectivityObserver

        private val HANDLER_THREAD_NAME = "network_task_handler"
        //  private static final String SOCKET_HOST_URL = "www.google.com";
        private val SOCKET_HOST_URL = "https://www.google.com"
        private val SOCKET_HOST_PORT = 80
        private val CONNECTION_TIME_OUT = 2000 //ms
        private val READ_TIME_OUT = 8000 //ms

        /**
         * @param app Application context
         * @return Singleton instance of manager
         */
        fun init(activity: AppCompatActivity): NetworkConnectivityObserver {
            if (!::instance.isInitialized) {
                instance = NetworkConnectivityObserver(activity)
            }
            return instance
        }
    }

    /**
     * @param context Context
     * @return true if Network is available on device, otherwise false.
     */
    fun isNetworkConnected(context: Context): Boolean {
        val cm: ConnectivityManager? = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var ni: NetworkInfo? = null
        ni = cm?.activeNetworkInfo

        return ni != null
    }
}
