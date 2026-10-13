package com.azhan.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.azhan.music.MusicApp
import com.azhan.music.ui.favorites.FavoritesScreen
import com.azhan.music.ui.home.HomeScreen
import com.azhan.music.ui.lyrics.LyricsScreen
import com.azhan.music.ui.player.PlayerScreen
import com.azhan.music.ui.playlist.PlaylistScreen
import com.azhan.music.ui.search.SearchScreen
import com.azhan.music.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val PLAYLIST = "playlist"
    const val FAVORITES = "favorites"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot(app: MusicApp) {
    val navController = rememberNavController()
    // 用普通 state 传「当前打开的目录」，避免把 content:// 的 tree uri 塞进路由参数
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }

    val player = app.player
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val prefs by app.library.prefs.collectAsStateWithLifecycle()

    // 设置页的「到点动作」要在本次会话内生效（播放页里也能临时改）
    LaunchedEffect(prefs.sleepAction) { player.sleepAction = prefs.sleepAction }

    // 记住最后播放的歌，供下次启动恢复
    LaunchedEffect(player.currentSongId) {
        player.currentSongId?.let { app.library.rememberLastSong(it) }
    }

    // 启动时按设置把上次那首歌装回播放器（只装载、不自动播放）。只做一次。
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(playlists, prefs.autoResume, prefs.lastSongId) {
        if (restored || playlists.isEmpty() || player.currentSongId != null) return@LaunchedEffect
        val lastId = prefs.lastSongId ?: return@LaunchedEffect
        val playlist = playlists.firstOrNull { list -> list.songs.any { it.id == lastId } }
            ?: run { restored = true; return@LaunchedEffect }
        restored = true
        if (!prefs.autoResume) return@LaunchedEffect
        val index = playlist.songs.indexOfFirst { it.id == lastId }
        player.prepare(playlist.songs, index)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                app = app,
                onOpenPlaylist = { id ->
                    selectedPlaylistId = id
                    navController.navigate(Routes.PLAYLIST) { launchSingleTop = true }
                },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) { launchSingleTop = true } },
                onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                app = app,
                onBack = { navController.popBackStack() },
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
