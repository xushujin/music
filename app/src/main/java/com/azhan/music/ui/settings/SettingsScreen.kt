package com.azhan.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.BuildConfig
import com.azhan.music.MusicApp
import com.azhan.music.data.SleepAction
import com.azhan.music.data.ThemeMode
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuButton
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuSegmented
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.NeuSwitch
import com.azhan.music.update.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 设置页：全局配置 + 关于软件。
 * 外观（深色/浅色）**故意没有** —— 拟态风格依赖"元素与背景同色、靠阴影分层"，
 * 深色背景上阴影会糊成一片，所以 App 固定浅色（见 README「设计取舍」）。
 *
 * 「数据」区的操作反馈**不走系统 Toast**：
 * App 不申请通知权限，而国内 ROM 常把 Toast 与通知开关绑定，通知关掉后 Toast 会被系统丢掉，
 * 用户点了会以为没反应。这里改成 Snackbar + 卡片里保留一行「上次操作」结果，必定可见。
 * 扫描期间按钮禁用并显示实时进度，避免重复点击叠加多次扫描。
 */
@Composable
fun SettingsScreen(
    app: MusicApp,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val prefs by app.library.prefs.collectAsStateWithLifecycle()
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    val updateState by app.update.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var rescanning by remember { mutableStateOf(false) }
    var confirmClearFavorites by remember { mutableStateOf(false) }
    var showInstallHint by remember { mutableStateOf(false) }
    // 卡片里保留最近一次操作的结果：Snackbar 会自己消失，这行不会
    var lastResult by remember { mutableStateOf<String?>(null) }

    val songCount = remember(playlists) { playlists.sumOf { it.songs.size } }
    val favoriteCount = remember(playlists, favorites) { app.library.favoriteSongs().size }

    Scaffold(
        containerColor = Neu.Background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = NeuShapes.chip,
                    containerColor = Neu.SnackbarBackground,
                    contentColor = Neu.SnackbarContent,
                )
            }
        },
    ) { padding ->
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

            // ---- 外观 ----
            SettingCard(
                title = "外观",
                subtitle = "深色是单独一套拟态配色，光晕强度另外调过，不是简单反色",
            ) {
                NeuSegmented(
                    options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                    selected = prefs.themeMode,
                    onSelect = { mode ->
                        scope.launch { app.library.updatePrefs { it.copy(themeMode = mode) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        }
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
            SettingCard(
                title = "数据",
                subtitle = if (playlists.isEmpty()) {
                    "还没有添加目录"
                } else {
                    "共 ${playlists.size} 个目录 · $songCount 首"
                },
            ) {
                ActionButton(
                    text = if (rescanning) "正在扫描…" else "重新扫描全部目录",
                    enabled = !rescanning && playlists.isNotEmpty(),
                    onClick = {
                        if (!rescanning) {
                            rescanning = true
                            lastResult = "开始扫描…"
                            scope.launch {
                                val summary = try {
                                    app.library.rescanAll()
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (t: Throwable) {
                                    null
                                }
                                rescanning = false
                                val message = when {
                                    summary == null -> "扫描失败，请检查目录是否还有访问权限"
                                    summary.failed > 0 ->
                                        "扫描完成：${summary.playlists - summary.failed} 个目录，共 ${summary.songs} 首" +
                                            "（${summary.failed} 个目录失败）"
                                    else -> "扫描完成：${summary.playlists} 个目录，共 ${summary.songs} 首"
                                }
                                lastResult = message
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                )

                // 扫描进度：目录名 + 已找到数量，每进一个目录刷新一次
                if (rescanning) {
                    val progress = scanning
                    Text(
                        text = buildString {
                            append("正在扫描")
                            progress?.playlistName?.takeIf { it.isNotBlank() }?.let { append("「$it」") }
                            progress?.currentDir?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                            append(" · 已找到 ${progress?.found ?: 0} 首")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Neu.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = "清空收藏",
                    enabled = favoriteCount > 0,
                    onClick = { confirmClearFavorites = true },
                )

                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = "重置歌词时间微调",
                    enabled = app.lyricPrefs.offsetCount > 0,
                    onClick = {
                        val count = app.lyricPrefs.resetAllOffsets()
                        val message = if (count > 0) {
                            "已重置 $count 首歌的歌词微调"
                        } else {
                            "没有需要重置的微调"
                        }
                        lastResult = message
                        scope.launch {
                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                        }
                    },
                )

                lastResult?.let { text ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "上次操作：$text",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neu.TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // ---- 更新 ----
            SettingCard(
                title = "更新",
                subtitle = "只有你点「检查更新」时才会访问 GitHub，不自动联网、不上报任何信息",
            ) {
                Text(
                    text = "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Neu.TextPrimary,
                )
                Spacer(Modifier.height(10.dp))

                when (val state = updateState) {
                    UpdateState.Idle -> ActionButton(text = "检查更新") { app.update.check() }

                    UpdateState.Checking -> ActionButton(text = "正在检查…", enabled = false) {}

                    UpdateState.Latest -> {
                        Text(
                            text = "已是最新版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = Neu.TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton(text = "再检查一次") { app.update.check() }
                    }

                    is UpdateState.Available -> {
                        Text(
                            text = "发现 v${state.version} · ${formatSize(state.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Neu.Accent,
                        )
                        if (state.notes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = state.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = Neu.TextSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        ActionButton(text = "下载更新") { app.update.download() }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { app.update.openReleasePage(state.pageUrl) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("在浏览器里查看发布页", color = Neu.Accent)
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = "正在下载 ${(state.progress * 100).toInt()}%" +
                                "  ·  ${formatSize(state.downloaded)} / ${formatSize(state.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Neu.TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Neu.Accent,
                            trackColor = Neu.Shadow.copy(alpha = 0.25f),
                            strokeCap = StrokeCap.Round,
                        )
                    }

                    is UpdateState.Ready -> {
                        Text(
                            text = "下载完成，可以安装了",
                            style = MaterialTheme.typography.bodySmall,
                            color = Neu.TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton(text = "安装 v${state.version}") {
                            if (!app.update.install(state.file)) showInstallHint = true
                        }
                    }

                    is UpdateState.Failed -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton(text = "重试") { app.update.check() }
                    }
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
                    text = "只播放你手机里已有的音频，音频不上传、不申请存储权限。" +
                        "目录通过系统文件选择器授权，随时可以在列表里移除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // 清空收藏是破坏性操作，先确认再执行
    if (confirmClearFavorites) {
        AlertDialog(
            onDismissRequest = { confirmClearFavorites = false },
            shape = NeuShapes.card,
            containerColor = Neu.Background,
            titleContentColor = Neu.TextPrimary,
            textContentColor = Neu.TextSecondary,
            title = {
                Text(
                    text = "清空收藏？",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "将从收藏里移除 $favoriteCount 首歌曲，音频文件本身不会被删除。" +
                        "这个操作不能撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearFavorites = false
                        scope.launch {
                            val count = app.library.clearFavorites()
                            val message = if (count > 0) "已清空 $count 首收藏" else "收藏已经是空的"
                            lastResult = message
                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                        }
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearFavorites = false }) {
                    Text("取消", color = Neu.TextSecondary)
                }
            },
        )
    }

    // 系统不允许安装未知应用时的引导
    if (showInstallHint) {
        AlertDialog(
            onDismissRequest = { showInstallHint = false },
            shape = NeuShapes.card,
            containerColor = Neu.Background,
            titleContentColor = Neu.TextPrimary,
            textContentColor = Neu.TextSecondary,
            title = { Text("需要允许安装应用", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "系统默认不允许 App 自己安装 APK。请在接下来的设置页里允许「轻响」" +
                        "安装未知应用，然后回来再点一次「安装」。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallHint = false
                        app.update.openInstallPermission()
                    },
                ) {
                    Text("去设置", color = Neu.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallHint = false }) {
                    Text("取消", color = Neu.TextSecondary)
                }
            },
        )
    }
}

/** 下载体积展示：小于 1 MB 用 KB，避免出现 0.0 MB。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024f / 1024f)
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
private fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    NeuButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Neu.TextPrimary else Neu.TextSecondary.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}
