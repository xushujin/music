package com.azhan.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.azhan.music.MusicApp
import com.azhan.music.ui.home.HomeScreen
import com.azhan.music.ui.lyrics.LyricsScreen
import com.azhan.music.ui.player.PlayerScreen
import com.azhan.music.ui.playlist.PlaylistScreen

object Routes {
    const val HOME = "home"
    const val PLAYLIST = "playlist"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
}

@Composable
fun AppRoot(app: MusicApp) {
    val navController = rememberNavController()
    // 用普通 state 传「当前打开的目录」，避免把 content:// 的 tree uri 塞进路由参数
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                app = app,
                onOpenPlaylist = { id ->
                    selectedPlaylistId = id
                    navController.navigate(Routes.PLAYLIST) { launchSingleTop = true }
                },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
            )
        }
        composable(Routes.PLAYLIST) {
            PlaylistScreen(
                app = app,
                playlistId = selectedPlaylistId,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onOpenLyrics = { navController.navigate(Routes.LYRICS) { launchSingleTop = true } },
            )
        }
        composable(Routes.LYRICS) {
            LyricsScreen(app = app, onBack = { navController.popBackStack() })
        }
    }
}
