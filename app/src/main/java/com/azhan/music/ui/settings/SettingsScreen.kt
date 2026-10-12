package com.azhan.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.BuildConfig
import com.azhan.music.MusicApp
import com.azhan.music.data.SleepAction
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuButton
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuSegmented
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.NeuSwitch
import kotlinx.coroutines.launch

/**
 * 设置页：全局配置 + 关于软件。
 * 外观（深色/浅色）**故意没有** —— 拟态风格依赖"元素与背景同色、靠阴影分层"，
 * 深色背景上阴影会糊成一片，所以 App 固定浅色（见 README「设计取舍」）。
 */
@Composable
fun SettingsScreen(
    app: MusicApp,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs by app.library.prefs.collectAsStateWithLifecycle()

    Scaffold(containerColor = Neu.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Neu.Background)
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "返回",
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Neu.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- 播放 ----
            SettingCard(title = "播放") {
                SwitchRow(
                    title = "启动时恢复上次播放",
                    subtitle = "只把上次那首歌装回播放器，不会自动开始播放",
                    checked = prefs.autoResume,
                    onCheckedChange = { value ->
                        scope.launch { app.library.updatePrefs { it.copy(autoResume = value) } }
                    },
                )
            }

            // ---- 定时退出 ----
            SettingCard(
                title = "定时退出",
                subtitle = "倒计时到点后的默认动作，播放页里可以临时改",
            ) {
                NeuSegmented(
                    options = listOf(SleepAction.EXIT, SleepAction.PAUSE),
                    selected = prefs.sleepAction,
                    onSelect = { action ->
                        scope.launch {
                            app.library.updatePrefs { it.copy(sleepAction = action) }
                            app.player.sleepAction = action
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { if (it == SleepAction.EXIT) "退出应用" else "仅暂停" },
                )
            }

            // ---- 数据 ----
            SettingCard(title = "数据") {
                ActionButton(text = "重新扫描全部目录") {
                    scope.launch {
                        val count = app.library.rescanAll()
                        Toast.makeText(context, "重新扫描完成，共 $count 首", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(text = "清空收藏") {
                    scope.launch {
                        app.library.clearFavorites()
                        Toast.makeText(context, "已清空收藏", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(text = "重置歌词时间微调") {
                    app.lyricPrefs.resetAllOffsets()
                    Toast.makeText(context, "已重置全部歌词微调", Toast.LENGTH_SHORT).show()
                }
            }

            // ---- 关于 ----
            SettingCard(title = "关于") {
                Text(
                    text = "轻响 · 本地音乐播放器   v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Neu.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "只播放你手机里已有的音频，不联网、不上传、不申请存储权限。" +
                        "目录通过系统文件选择器授权，随时可以在列表里移除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 分区卡片：标题 + 可选副标题 + 一块凹陷留白的拟态面板。 */
@Composable
private fun SettingCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Neu.TextPrimary,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Neu.TextSecondary,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        NeuBox(
            modifier = Modifier.fillMaxWidth(),
            shape = NeuShapes.card,
            color = Neu.Background,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Neu.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neu.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        NeuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    NeuButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Neu.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}
