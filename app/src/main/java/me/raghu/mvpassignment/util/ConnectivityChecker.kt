package me.raghu.mvpassignment.util

/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.*


/**
 * Lifecycle aware connectivity checker that exposes the network connected status via a LiveData.
 *
 * The loss of connectivity while the user scrolls through the feed should NOT be a blocker for the
 * user.
 *
 * The loss of connectivity when the activity is resumed should be a blocker for the user
 * (since we can't get feed items) - in onResume, we should get the connectivity status. If we
 * are NOT connected then we register a listener and wait to be notified. Only once we are
 * connected, we stop listening to connectivity.¬
 */
class ConnectivityChecker(
    private val connectivityManager: ConnectivityManager
) : LifecycleObserver {

    private var monitoringConnectivity = false

    private val _connectedStatus = MutableLiveData<Boolean>()
    val connectedStatus: LiveData<Boolean>
        get() = _connectedStatus

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _connectedStatus.postValue(true)
            // we are connected, so we can stop listening
            connectivityManager.unregisterNetworkCallback(this)
            monitoringConnectivity = false
        }

        override fun onLost(network: Network) {
            _connectedStatus.postValue(false)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun stopMonitoringConnectivity() {
        if (monitoringConnectivity) {
            connectivityManager.unregisterNetworkCallback(connectivityCallback)
            monitoringConnectivity = false
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun startMonitoringConnectivity() {
        val connected = isConnected()
        _connectedStatus.postValue(connected)
        if (!connected) {
            // we don't have internet connection, so we listen to notifications in connection status
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                connectivityCallback
            )
            monitoringConnectivity = true
        }
    }

    private fun isConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        return true
                    }
                }
            }
        } else {
            val nwInfo = connectivityManager.activeNetworkInfo ?: return false
            return nwInfo.isConnected
        }
        return false
    }
}
