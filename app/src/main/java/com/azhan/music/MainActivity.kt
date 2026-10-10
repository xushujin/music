package com.azhan.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        setContent {
            MusicTheme {
                AppRoot(app)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as MusicApp).player.release()
        }
        super.onDestroy()
    }
}
