package dev.emusic.data.preferences

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    context: Context,
    preferencesRepository: AppPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())

    val isOnline: StateFlow<Boolean> = combine(
        _isConnected,
        preferencesRepository.preferencesFlow.map { it.forceOfflineMode },
    ) { connected, forceOffline ->
        connected && !forceOffline
    }.stateIn(scope, SharingStarted.Eagerly, _isConnected.value)

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
            }

            override fun onLost(network: Network) {
                // Re-check actual connectivity — another network may still be active
                _isConnected.value = checkCurrentConnectivity()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isConnected.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
