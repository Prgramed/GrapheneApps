package dev.emusic.ui.stats

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.emusic.data.db.dao.AlbumPlayCount
import dev.emusic.data.db.dao.ArtistPlayCount
import dev.emusic.data.db.dao.DailyListening
import dev.emusic.data.db.dao.GenrePlayCount
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackPlayCount
import dev.emusic.data.db.entity.ScrobbleEntity
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class TimeRange(val label: String, val sinceMs: () -> Long) {
    ALL_TIME("All Time", { 0L }),
    THIS_YEAR("This Year", {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }),
    THIS_MONTH("This Month", {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }),
    LAST_30_DAYS("Last 30 Days", { System.currentTimeMillis() - 30L * 86400000 }),
}

data class StatsUiState(
    val isLoading: Boolean = true,
    val timeRange: TimeRange = TimeRange.ALL_TIME,
    val topTracks: List<TrackPlayCount> = emptyList(),
    val topArtists: List<ArtistPlayCount> = emptyList(),
    val topAlbums: List<AlbumPlayCount> = emptyList(),
    val totalListeningMs: Long = 0,
    val dailyListening: List<DailyListening> = emptyList(),
    val topGenres: List<GenrePlayCount> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val firstScrobble: ScrobbleEntity? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val scrobbleDao: ScrobbleDao,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun setTimeRange(range: TimeRange) {
        _uiState.value = _uiState.value.copy(timeRange = range)
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            withContext(Dispatchers.IO) {
                val since = _uiState.value.timeRange.sinceMs()
                val topTracks = scrobbleDao.getTopTracksWithCount(since, 50)
                val topArtists = scrobbleDao.getTopArtists(since, 20)
                val topAlbums = scrobbleDao.getTopAlbums(since, 20)
                val totalMs = scrobbleDao.getTotalListeningTimeMs(since)
                val daily = scrobbleDao.getDailyListening(
                    System.currentTimeMillis() - 30L * 86400000,
                )
                val genres = scrobbleDao.getTopGenres(since, 10)
                val firstScrobble = scrobbleDao.getFirstScrobble()

                val (currentStreak, longestStreak) = calculateStreaks()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    topTracks = topTracks,
                    topArtists = topArtists,
                    topAlbums = topAlbums,
                    totalListeningMs = totalMs,
                    dailyListening = daily,
                    topGenres = genres,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    firstScrobble = firstScrobble,
                )
            }
        }
    }

    private suspend fun calculateStreaks(): Pair<Int, Int> {
        val days = scrobbleDao.getDistinctScrobbleDays()
        if (days.isEmpty()) return 0 to 0

        val today = System.currentTimeMillis() / 86400000
        var streak = 1
        var longest = 1
        var current = 0

        for (i in 1 until days.size) {
            if (days[i - 1] - days[i] == 1L) {
                streak++
            } else {
                longest = maxOf(longest, streak)
                streak = 1
            }
        }
        longest = maxOf(longest, streak)

        // Current streak: count from today/yesterday backwards
        current = if (days[0] == today || days[0] == today - 1) {
            var s = 1
            for (i in 1 until days.size) {
                if (days[i - 1] - days[i] == 1L) s++ else break
            }
            s
        } else 0

        return current to longest
    }

    fun getCoverArtUrl(id: String): String = libraryRepository.getCoverArtUrl(id, 100)

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val tracks = _uiState.value.topTracks
                if (tracks.isEmpty()) return@withContext

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val fileName = "emusic_top_tracks_$dateStr.csv"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                val file = File(downloadsDir, fileName)

                file.bufferedWriter().use { writer ->
                    writer.write("Rank,TrackID,Title,Artist,PlayCount")
                    writer.newLine()
                    tracks.forEachIndexed { index, track ->
                        val title = track.title.replace(",", " ")
                        val artist = track.artist.replace(",", " ")
                        writer.write("${index + 1},${track.trackId},$title,$artist,${track.count}")
                        writer.newLine()
                    }
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    ))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Stats"))
            }
        }
    }
}
