package dev.emusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ArtistDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.usecase.SyncLibraryUseCase
import dev.emusic.domain.usecase.SyncProgress
import dev.emusic.playback.QueueManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    val queueManager: QueueManager,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
) : ViewModel() {

    val artists: Flow<PagingData<Artist>> =
        libraryRepository.observeArtists().cachedIn(viewModelScope)

    private val _trackCount = MutableStateFlow(0)
    val trackCount: StateFlow<Int> = _trackCount.asStateFlow()

    private val _albumCount = MutableStateFlow(0)
    val albumCount: StateFlow<Int> = _albumCount.asStateFlow()

    private val _artistCount = MutableStateFlow(0)
    val artistCount: StateFlow<Int> = _artistCount.asStateFlow()

    init {
        refreshCounts()
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            _trackCount.value = trackDao.count()
            _albumCount.value = albumDao.count()
            _artistCount.value = artistDao.count()
        }
    }

    private val _albumSort = MutableStateFlow(AlbumSort.NAME)
    val albumSort: StateFlow<AlbumSort> = _albumSort.asStateFlow()

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _availableGenres = MutableStateFlow<List<String>>(emptyList())
    val availableGenres: StateFlow<List<String>> = _availableGenres.asStateFlow()

    val albums: Flow<PagingData<Album>> = combine(_albumSort, _filter) { sort, filter ->
        sort to filter
    }.flatMapLatest { (sort, filter) ->
        val sortSql = when (sort) {
            AlbumSort.NAME -> "name COLLATE NOCASE"
            AlbumSort.YEAR_DESC -> "year DESC, name COLLATE NOCASE"
            AlbumSort.YEAR_ASC -> "year ASC, name COLLATE NOCASE"
            AlbumSort.MOST_PLAYED -> "playCount DESC, name COLLATE NOCASE"
            AlbumSort.RANDOM -> "RANDOM()"
        }
        val whereClauses = mutableListOf<String>()
        if (filter.genres.isNotEmpty()) {
            val genreList = filter.genres.joinToString(",") { "'${it.replace("'", "''")}'" }
            whereClauses.add("genre IN ($genreList)")
        }
        if (filter.starredOnly) whereClauses.add("starred = 1")
        if (filter.decades.isNotEmpty()) {
            val decadeConditions = filter.decades.map { decade ->
                when (decade) {
                    "Older" -> "year < 1960"
                    else -> {
                        val start = decade.removeSuffix("s").toIntOrNull() ?: 2000
                        "(year >= $start AND year < ${start + 10})"
                    }
                }
            }
            whereClauses.add("(${decadeConditions.joinToString(" OR ")})")
        }
        val filterWhere = whereClauses.joinToString(" AND ")

        libraryRepository.observeAlbumsSorted(sortSql, filterWhere)
    }.cachedIn(viewModelScope)

    val tracks: Flow<PagingData<Track>> =
        libraryRepository.observeAllTracks().cachedIn(viewModelScope)

    val syncProgress: StateFlow<SyncProgress?> = syncLibraryUseCase.progress

    init {
        viewModelScope.launch {
            trackDao.observeGenresWithCount().collect { genres ->
                _availableGenres.value = genres.map { it.genre }
            }
        }
    }

    fun setAlbumSort(sort: AlbumSort) { _albumSort.value = sort }
    fun setFilter(filter: LibraryFilter) { _filter.value = filter }

    fun getCoverArtUrl(id: String, size: Int = 300): String =
        libraryRepository.getCoverArtUrl(id, size)

    fun playTrack(track: Track) {
        queueManager.play(listOf(track), 0)
    }

    fun toggleStar(track: Track) {
        viewModelScope.launch {
            if (track.starred) libraryRepository.unstarTrack(track.id)
            else libraryRepository.starTrack(track.id)
        }
    }

    fun sync() {
        viewModelScope.launch {
            syncLibraryUseCase().collect { progress ->
                if (progress.done) refreshCounts()
            }
        }
    }
}
