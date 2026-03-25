package dev.emusic.ui.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.GenreCount
import dev.emusic.data.db.dao.TrackDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreBrowseViewModel @Inject constructor(
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _genres = MutableStateFlow<List<GenreCount>>(emptyList())
    val genres: StateFlow<List<GenreCount>> = _genres.asStateFlow()

    init {
        viewModelScope.launch {
            trackDao.observeGenresWithCount().collect { genreCounts ->
                // Group genres with < 5 tracks into "Other"
                val (main, small) = genreCounts.partition { it.count >= 5 }
                val otherCount = small.sumOf { it.count }
                _genres.value = if (otherCount > 0) {
                    main + GenreCount("Other", otherCount)
                } else {
                    main
                }
            }
        }
    }
}
