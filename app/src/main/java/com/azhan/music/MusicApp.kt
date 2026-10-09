package com.azhan.music

import android.app.Application
import com.azhan.music.data.LibraryRepository
import com.azhan.music.data.LyricPrefsState
import com.azhan.music.data.MusicStore
import com.azhan.music.lyrics.LyricsRepository
import com.azhan.music.player.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖容器。整个 App 就这几个单例，上 Hilt/Koin 反而是负担。
 */
class MusicApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val store: MusicStore by lazy { MusicStore(this) }
    val library: LibraryRepository by lazy { LibraryRepository(this, store) }
    val lyrics: LyricsRepository by lazy { LyricsRepository(this) }
    val player: PlaybackController by lazy { PlaybackController(this) }
    val lyricPrefs: LyricPrefsState by lazy { LyricPrefsState(store) }

    override fun onCreate() {
        super.onCreate()
        // 曲库是从本地 JSON 直接读出来的，不扫描目录，所以启动几乎无耗时
        appScope.launch { library.load() }
        lyricPrefs.load()
    }
}
