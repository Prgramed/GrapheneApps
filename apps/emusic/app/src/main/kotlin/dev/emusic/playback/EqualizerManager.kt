package dev.emusic.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import dev.emusic.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerState(
    val enabled: Boolean = false,
    val numberOfBands: Int = 5,
    val bandFrequencies: List<Int> = emptyList(),
    val bandLevels: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val activePresetName: String = "Flat",
    val supported: Boolean = true,
)

@Singleton
class EqualizerManager @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
) {
    companion object {
        private const val TAG = "EqualizerManager"

        val BUILT_IN_PRESETS: Map<String, IntArray> = linkedMapOf(
            "Flat" to intArrayOf(0, 0, 0, 0, 0),
            "Bass Boost" to intArrayOf(600, 400, 0, 0, 0),
            "Treble" to intArrayOf(0, 0, 0, 400, 600),
            "Vocal" to intArrayOf(-200, 0, 400, 400, 0),
            "Rock" to intArrayOf(400, 200, -100, 200, 400),
            "Electronic" to intArrayOf(500, 300, 0, -100, 300),
            "Classical" to intArrayOf(300, 100, 0, 100, 300),
            "Hip Hop" to intArrayOf(500, 400, 0, 100, 300),
        )
    }

    private val scope = CoroutineScope(SupervisorJob())
    private var restoreJob: kotlinx.coroutines.Job? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun initialize(audioSessionId: Int) {
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val numBands = eq.numberOfBands.toInt()
            val freqs = (0 until numBands).map { eq.getCenterFreq(it.toShort()) }
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            bassBoost = try { BassBoost(0, audioSessionId) } catch (e: Exception) {
                Log.w(TAG, "BassBoost not supported", e); null
            }
            virtualizer = try { Virtualizer(0, audioSessionId) } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not supported", e); null
            }

            _state.value = EqualizerState(
                numberOfBands = numBands,
                bandFrequencies = freqs,
                bandLevels = List(numBands) { 0 },
                minLevel = minLevel,
                maxLevel = maxLevel,
                supported = true,
            )

            // Restore saved state
            restoreJob?.cancel()
            restoreJob = scope.launch { restoreFromPreferences() }
        } catch (e: Exception) {
            Log.e(TAG, "Equalizer not supported on this device", e)
            _state.value = EqualizerState(supported = false)
        }
    }

    fun release() {
        restoreJob?.cancel()
        try { equalizer?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        try { virtualizer?.release() } catch (_: Exception) { }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        _state.value = _state.value.copy(enabled = enabled)
        scope.launch { preferencesRepository.updateEqEnabled(enabled) }
    }

    fun setBandLevel(band: Int, level: Int) {
        val eq = equalizer ?: return
        eq.setBandLevel(band.toShort(), level.toShort())
        val newLevels = _state.value.bandLevels.toMutableList()
        if (band in newLevels.indices) newLevels[band] = level
        _state.value = _state.value.copy(bandLevels = newLevels, activePresetName = "Custom")
        scope.launch {
            preferencesRepository.updateEqBandLevels(newLevels.joinToString(","))
            preferencesRepository.updateEqActivePreset("Custom")
        }
    }

    fun setBassBoost(strength: Int) {
        try { bassBoost?.setStrength(strength.toShort()) } catch (_: Exception) { }
        _state.value = _state.value.copy(bassBoostStrength = strength)
        scope.launch { preferencesRepository.updateEqBassBoost(strength) }
    }

    fun setVirtualizer(strength: Int) {
        try { virtualizer?.setStrength(strength.toShort()) } catch (_: Exception) { }
        _state.value = _state.value.copy(virtualizerStrength = strength)
        scope.launch { preferencesRepository.updateEqVirtualizer(strength) }
    }

    fun applyPreset(name: String, levels: List<Int>, bassBoostVal: Int = 0) {
        val eq = equalizer ?: return
        val numBands = _state.value.numberOfBands
        for (i in 0 until numBands.coerceAtMost(levels.size)) {
            eq.setBandLevel(i.toShort(), levels[i].toShort())
        }
        try { bassBoost?.setStrength(bassBoostVal.toShort()) } catch (_: Exception) { }

        val appliedLevels = (0 until numBands).map { i ->
            if (i < levels.size) levels[i] else 0
        }
        _state.value = _state.value.copy(
            bandLevels = appliedLevels,
            bassBoostStrength = bassBoostVal,
            activePresetName = name,
        )
        scope.launch {
            preferencesRepository.updateEqBandLevels(appliedLevels.joinToString(","))
            preferencesRepository.updateEqBassBoost(bassBoostVal)
            preferencesRepository.updateEqActivePreset(name)
        }
    }

    private suspend fun restoreFromPreferences() {
        val prefs = preferencesRepository.preferencesFlow.first()
        val eq = equalizer ?: return

        if (prefs.eqBandLevels.isNotBlank()) {
            val levels = prefs.eqBandLevels.split(",").mapNotNull { it.trim().toIntOrNull() }
            val numBands = _state.value.numberOfBands
            for (i in 0 until numBands.coerceAtMost(levels.size)) {
                eq.setBandLevel(i.toShort(), levels[i].toShort())
            }
            val appliedLevels = (0 until numBands).map { i ->
                if (i < levels.size) levels[i] else 0
            }
            _state.value = _state.value.copy(bandLevels = appliedLevels)
        }

        try { bassBoost?.setStrength(prefs.eqBassBoost.toShort()) } catch (_: Exception) { }
        try { virtualizer?.setStrength(prefs.eqVirtualizer.toShort()) } catch (_: Exception) { }

        val enabled = prefs.equalizerEnabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled

        _state.value = _state.value.copy(
            enabled = enabled,
            bassBoostStrength = prefs.eqBassBoost,
            virtualizerStrength = prefs.eqVirtualizer,
            activePresetName = prefs.eqActivePreset,
        )
    }
}
