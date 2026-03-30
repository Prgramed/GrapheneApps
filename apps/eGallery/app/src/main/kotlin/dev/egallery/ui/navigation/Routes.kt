package dev.egallery.ui.navigation

object Routes {
    const val TIMELINE = "timeline"
    const val FOLDER_BROWSER = "folder_browser"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album_detail/{albumId}"
    const val PEOPLE = "people"
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val MAP = "map"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val EDIT = "edit/{nasId}"
    const val URI_VIEWER = "uri_viewer/{uri}"
    const val PHOTO_VIEWER = "photo_viewer/{nasId}"
    const val VIDEO_PLAYER = "video_player/{nasId}"

    fun uriViewer(uri: String): String = "uri_viewer/${android.net.Uri.encode(uri)}"
    fun edit(nasId: String): String = "edit/$nasId"
    fun albumDetail(albumId: String): String = "album_detail/$albumId"
    fun personDetail(personId: String): String = "person_detail/$personId"
    fun photoViewer(nasId: String): String = "photo_viewer/$nasId"
    fun videoPlayer(nasId: String): String = "video_player/$nasId"
}
