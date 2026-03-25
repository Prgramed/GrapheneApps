package dev.emusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.GenreCount
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.domain.model.Album
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.usecase.GetHomeScreenUseCase
import dev.emusic.domain.usecase.GetSuggestionsUseCase
import dev.emusic.domain.usecase.SmartQuickMixUseCase
import dev.emusic.domain.usecase.HomeData
import dev.emusic.playback.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val data: HomeData = HomeData(),
    val suggestions: List<Album>? = null,
    val topGenres: List<GenreCount> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeScreenUseCase: GetHomeScreenUseCase,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val smartQuickMixUseCase: SmartQuickMixUseCase,
    private val libraryRepository: LibraryRepository,
    private val queueManager: QueueManager,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            trackDao.observeGenresWithCount().collect { genres ->
                _uiState.value = _uiState.value.copy(topGenres = genres.take(8))
            }
        }
    }

    fun refresh() {
        loadHome(forceRefresh = true)
    }

    fun playQuickMix() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val songs = smartQuickMixUseCase()
            if (songs.isNotEmpty()) {
                queueManager.play(songs, 0)
            }
        }
    }

    fun getCoverArtUrl(id: String): String =
        libraryRepository.getCoverArtUrl(id)

    private fun loadHome(forceRefresh: Boolean = false) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Room reads (may be slow on cold start due to DB initialization)
            val cachedData = getHomeScreenUseCase(forceRefresh = false)
            val suggestions = try {
                getSuggestionsUseCase().ifEmpty { null }
            } catch (_: Exception) { null }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                data = cachedData,
                suggestions = suggestions,
            )

            // API refresh if stale
            if (forceRefresh || getHomeScreenUseCase.isStale()) {
                val freshData = getHomeScreenUseCase(forceRefresh = true)
                _uiState.value = _uiState.value.copy(data = freshData)
            }
        }
    }
}
