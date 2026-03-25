package dev.emusic.ui.settings.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.EqPresetDao
import dev.emusic.data.db.entity.EqPresetEntity
import dev.emusic.playback.EqualizerManager
import dev.emusic.playback.EqualizerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val eqPresetDao: EqPresetDao,
) : ViewModel() {

    val equalizerState: StateFlow<EqualizerState> = equalizerManager.state

    val customPresets: StateFlow<List<EqPresetEntity>> = eqPresetDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)
    fun setBandLevel(band: Int, level: Int) = equalizerManager.setBandLevel(band, level)
    fun setBassBoost(strength: Int) = equalizerManager.setBassBoost(strength)
    fun setVirtualizer(strength: Int) = equalizerManager.setVirtualizer(strength)

    fun applyBuiltInPreset(name: String) {
        val levels = EqualizerManager.BUILT_IN_PRESETS[name] ?: return
        equalizerManager.applyPreset(name, levels.toList())
    }

    fun applyCustomPreset(preset: EqPresetEntity) {
        val levels = preset.bandLevels.split(",").mapNotNull { it.trim().toIntOrNull() }
        equalizerManager.applyPreset(preset.name, levels, preset.bassBoostStrength)
    }

    fun savePreset(name: String) {
        val state = equalizerState.value
        viewModelScope.launch {
            eqPresetDao.upsert(
                EqPresetEntity(
                    name = name,
                    bandLevels = state.bandLevels.joinToString(","),
                    bassBoostStrength = state.bassBoostStrength,
                ),
            )
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { eqPresetDao.deleteById(id) }
    }
}
