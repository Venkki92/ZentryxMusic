package com.zentryx.music.navigation

/**
 * Type-safe navigation routes for Jetpack Compose Navigation.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Library : Screen("library")
    data object Player : Screen("player")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object DspConsole : Screen("dsp_console")
    data object ScanStatus : Screen("scan_status")
    data object AlbumDetail : Screen("album/{albumName}/{artistName}") {
        fun createRoute(albumName: String, artistName: String): String =
            "album/${java.net.URLEncoder.encode(albumName, "UTF-8")}/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
}
