package dev.eweather.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.eweather.data.preferences.AppPreferences
import dev.eweather.data.preferences.AppPreferencesRepository
import dev.eweather.data.preferences.TemperatureUnit
import dev.eweather.data.preferences.WindUnit
import dev.eweather.data.worker.WeatherRefreshWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { preferencesRepository.updateTemperatureUnit(unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { preferencesRepository.updateWindUnit(unit) }
    }

    fun setRefreshInterval(hours: Int) {
        viewModelScope.launch {
            preferencesRepository.updateRefreshInterval(hours)
            WeatherRefreshWorker.schedule(appContext, hours)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNotifications(enabled) }
    }
}
