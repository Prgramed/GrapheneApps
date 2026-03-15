package com.prgramed.eprayer.feature.qibla

import com.prgramed.eprayer.domain.model.QiblaDirection

data class QiblaUiState(
    val qiblaDirection: QiblaDirection? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)
