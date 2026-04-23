package dev.emusic.ui.library

enum class AlbumSort(val label: String) {
    NAME("Name A-Z"),
    YEAR_DESC("Year (newest)"),
    YEAR_ASC("Year (oldest)"),
    MOST_PLAYED("Most Played"),
    RANDOM("Random"),
}

enum class ArtistSort(val label: String) {
    NAME("Name A-Z"),
    ALBUM_COUNT("Most Albums"),
}

enum class TrackSort(val label: String) {
    TITLE("Title A-Z"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
}

data class LibraryFilter(
    val genres: Set<String> = emptySet(),
    val decades: Set<String> = emptySet(),
    val downloadedOnly: Boolean = false,
    val starredOnly: Boolean = false,
) {
    val activeCount: Int get() = genres.size + decades.size +
        (if (downloadedOnly) 1 else 0) +
        (if (starredOnly) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}
