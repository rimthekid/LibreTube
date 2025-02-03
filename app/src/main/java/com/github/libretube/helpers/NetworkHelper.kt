package com.github.libretube.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

object NetworkHelper {
    /**
     * Detect whether network is available
     */
    fun isNetworkAvailable(context: Context): Boolean {
        // In case we are using a VPN, we return true since we might be using reverse tethering
        val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return false

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /**
     * Detect whether the current network is metered
     * @param context Context of the application
     * @return whether the network is metered or not
     */
    @Suppress("DEPRECATION")
    fun isNetworkMetered(context: Context): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        val activeNetworkInfo = connectivityManager.activeNetworkInfo

        // In case we are using nothing but a VPN, it should default to not metered
        if (activeNetworkInfo == null) {
            // activeNetworkInfo might return null instead of the VPN, so better check it explicitly
            val vpnInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_VPN)
            if (vpnInfo?.isConnected == true) {
                return false
            }
        } else if (activeNetworkInfo.type == ConnectivityManager.TYPE_VPN) {
            return false
        }

        return connectivityManager.isActiveNetworkMetered
    }
}
