package com.prgramed.econtacts.call

import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRouteOption(
    val route: Int,
    val label: String,
)

object AudioRouteManager {

    private var service: InCallService? = null
    private var lastAudioState: CallAudioState? = null

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val audioRoute: StateFlow<Int> = _audioRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<AudioRouteOption>>(emptyList())
    val availableRoutes: StateFlow<List<AudioRouteOption>> = _availableRoutes.asStateFlow()

    fun setService(inCallService: InCallService?) {
        service = inCallService
    }

    fun updateAudioState(state: CallAudioState) {
        lastAudioState = state
        _isMuted.value = state.isMuted
        _audioRoute.value = state.route

        val routes = mutableListOf<AudioRouteOption>()
        val mask = state.supportedRouteMask
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) {
            routes.add(AudioRouteOption(CallAudioState.ROUTE_EARPIECE, "Phone"))
        }
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) {
            routes.add(AudioRouteOption(CallAudioState.ROUTE_SPEAKER, "Speaker"))
        }
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
            val btName = state.activeBluetoothDevice?.name
                ?: state.supportedBluetoothDevices?.firstOrNull()?.name
            routes.add(AudioRouteOption(CallAudioState.ROUTE_BLUETOOTH, btName ?: "Bluetooth"))
        }
        _availableRoutes.value = routes
    }

    fun toggleMute() {
        val svc = service ?: return
        svc.setMuted(!_isMuted.value)
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
        val svc = service ?: return
        svc.setAudioRoute(route)
    }

    fun reset() {
        service = null
        lastAudioState = null
        _isMuted.value = false
        _audioRoute.value = CallAudioState.ROUTE_EARPIECE
        _availableRoutes.value = emptyList()
    }
}
