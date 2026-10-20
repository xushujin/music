package com.azhan.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.data.ThemeMode
import com.azhan.music.ui.AppRoot
import com.azhan.music.ui.theme.MusicTheme

/**
 * 不再在启动时弹通知权限申请。
 *
 * 后果：Android 13+ 上默认不显示播放通知（通知栏里没有播放/暂停/下一首）。
 * 但**后台播放本身照常工作** —— 没有 POST_NOTIFICATIONS 时 startForeground() 仍会成功，
 * 只是通知被系统丢掉，不会崩溃。
 *
 * 想恢复通知的用户可以在「系统设置 → 通知 → 轻响」里手动打开，
 * 所以 AndroidManifest 里仍然保留了权限声明。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MusicApp
        app.player.connect()

        // 定时退出到点：关掉界面（finishAndRemoveTask 同时把它从「最近任务」里移除）。
        // 播放暂停由 PlaybackController 自己完成，这里只负责收尾。
        app.player.onSleepTimerEnd = { finishAndRemoveTask() }

        setContent {
            // 外观三档：跟随系统 / 固定浅色 / 固定深色。prefs 加载完成后自动切换，不用重启
            val prefs by app.library.prefs.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MusicTheme(darkTheme = darkTheme) {
                AppRoot(app)
            }
        }
    }

    override fun onDestroy() {
        val app = application as MusicApp
        // 界面销毁后回调要摘掉，避免持有已销毁的 Activity
        app.player.onSleepTimerEnd = null
        if (isFinishing) {
            app.player.release()
        }
        super.onDestroy()
    }
}
