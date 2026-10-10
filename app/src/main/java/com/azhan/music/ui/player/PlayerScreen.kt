package com.azhan.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.azhan.music.MusicApp
import com.azhan.music.ui.common.findSong
import com.azhan.music.ui.common.formatTime
import com.azhan.music.ui.common.rememberLyrics
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuChip
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.NeuSlider
import com.azhan.music.ui.theme.neuInset
import kotlinx.coroutines.delay

/** 定时退出的预设时长（分钟）。 */
private val SLEEP_PRESETS = listOf(15, 30, 60, 90, 120)

/** 自定义时长的合法区间（分钟）。 */
private const val SLEEP_MIN_MINUTES = 1
private const val SLEEP_MAX_MINUTES = 600

@Composable
fun PlayerScreen(
    app: MusicApp,
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val player = app.player
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val song = remember(playlists, player.currentSongId) { playlists.findSong(player.currentSongId) }
    val lyrics = rememberLyrics(app, song)

    // 只有「当前行」变化时才重组，80ms 一次的位置刷新不会打穿整个界面
    val currentIndex by remember(lyrics, song?.id) {
        derivedStateOf { lyrics.indexAt(player.positionMs + app.lyricPrefs.offsetOf(song?.id)) }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var timerPanelVisible by remember { mutableStateOf(false) }
    val duration = player.durationMs
    val fraction = when {
        duration <= 0L -> 0f
        dragging -> dragFraction
        else -> (player.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }

    LaunchedEffect(player.errorMessage) {
        if (player.errorMessage == null) return@LaunchedEffect
        delay(4000)
        player.clearError()
    }

    Scaffold(containerColor = Neu.Background) { padding ->
        if (song == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Neu.Background)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有在播放的歌曲", color = Neu.TextSecondary)
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Neu.Background)
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 4.dp),
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
                        text = "正在播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Neu.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    SleepTimerButton(
                        remainingMs = player.sleepRemainingMs,
                        onClick = { timerPanelVisible = true },
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 中间不放封面占位方块：文件名直接放最上面，下面整块留给歌词
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Neu.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(18.dp))

                // 歌词预览：占满中间剩余空间（封面去掉后，歌词就是主视觉）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Neu.Background, NeuShapes.chip)
                        .neuInset(NeuShapes.chip, stroke = 2.5.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenLyrics,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val current = if (lyrics.isEmpty) null else lyrics.lines.getOrNull(currentIndex)?.text
                    val next = if (lyrics.isEmpty) null else lyrics.lines.getOrNull(currentIndex + 1)?.text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (current == null) {
                            Text(
                                text = "没有找到歌词 · 点这里查看",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Neu.TextSecondary,
                            )
                        } else {
                            Text(
                                text = current,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = Neu.Accent,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!next.isNullOrBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = next,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Neu.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                NeuSlider(
                    value = fraction,
                    onValueChange = {
                        dragging = true
                        dragFraction = it
                    },
                    onValueChangeFinished = {
                        if (dragging) {
                            player.seekToFraction(dragFraction)
                            dragging = false
                        }
                    },
                    enabled = duration > 0L,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatTime(player.positionMs), style = MaterialTheme.typography.labelMedium, color = Neu.TextSecondary)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = Neu.TextSecondary)
                }

                Spacer(Modifier.height(14.dp))

                // 控制区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeuIconButton(
                        icon = Icons.Filled.Shuffle,
                        onClick = { player.toggleShuffle() },
                        contentDescription = "随机播放",
                        size = 46.dp,
                        iconSize = 20.dp,
                        tint = if (player.shuffleEnabled) Neu.Accent else Neu.TextSecondary,
                    )
                    NeuIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        onClick = { player.previous() },
                        contentDescription = "上一首",
                        size = 58.dp,
                        iconSize = 28.dp,
                    )
                    // 主按钮：更大的凸起圆，按下变凹陷
                    NeuIconButton(
                        icon = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = { player.toggle() },
                        contentDescription = if (player.isPlaying) "暂停" else "播放",
                        size = 84.dp,
                        iconSize = 40.dp,
                        tint = Neu.Accent,
                    )
                    NeuIconButton(
                        icon = Icons.Filled.SkipNext,
                        onClick = { player.next() },
                        contentDescription = "下一首",
                        size = 58.dp,
                        iconSize = 28.dp,
                    )
                    NeuIconButton(
                        icon = when (player.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        onClick = { player.cycleRepeatMode() },
                        contentDescription = "循环模式",
                        size = 46.dp,
                        iconSize = 20.dp,
                        tint = if (player.repeatMode == Player.REPEAT_MODE_OFF) Neu.TextSecondary else Neu.Accent,
                    )
                }

                // 不单独放「全屏歌词」按钮：歌词区本身就是可点的，点一下即进全屏

                player.errorMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(20.dp))
            }

            // 点面板外的空白处收起
            if (timerPanelVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { timerPanelVisible = false },
                )
            }

            // 定时退出面板（底部浮层，默认收起）
            AnimatedVisibility(
                visible = timerPanelVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                SleepTimerPanel(
                    remainingMs = player.sleepRemainingMs,
                    activeMinutes = player.sleepTotalMinutes,
                    onStart = { player.startSleepTimer(it) },
                    onCancel = { player.cancelSleepTimer() },
                    onClose = { timerPanelVisible = false },
                )
            }
        }
    }
}

/**
 * 顶栏右侧的定时入口：未启用时是图标，启用后变成显示剩余时间的小胶囊。
 * 两种形态都点进设置面板。
 */
@Composable
private fun SleepTimerButton(remainingMs: Long, onClick: () -> Unit) {
    if (remainingMs <= 0L) {
        NeuIconButton(
            icon = Icons.Filled.Timer,
            onClick = onClick,
            contentDescription = "定时退出",
            size = 44.dp,
            iconSize = 20.dp,
            tint = Neu.TextSecondary,
        )
        return
    }

    Row(
        modifier = Modifier
            .background(Neu.Background, NeuShapes.pill)
            .neuInset(NeuShapes.pill, stroke = 2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Timer,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = Neu.Accent,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatTime(remainingMs),
            color = Neu.Accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 定时退出设置面板：预设时长 + 自定义分钟数；已启用时显示剩余并可取消。
 */
@Composable
private fun SleepTimerPanel(
    remainingMs: Long,
    activeMinutes: Int,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val customMinutes = custom.toIntOrNull()
    val customValid = customMinutes != null && customMinutes in SLEEP_MIN_MINUTES..SLEEP_MAX_MINUTES

    NeuBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        shape = RoundedCornerShape(24.dp),
        color = Neu.Background,
        blur = 14.dp,
        offset = 6.dp,
        highlight = Neu.Highlight,
        shadow = Neu.Shadow,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "定时退出",
                    color = Neu.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                NeuIconButton(
                    icon = Icons.Filled.Close,
                    onClick = onClose,
                    contentDescription = "收起",
                    size = 34.dp,
                    iconSize = 16.dp,
                    tint = Neu.TextSecondary,
                    color = Neu.Background,
                    highlight = Neu.Highlight,
                    shadow = Neu.Shadow,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "到点后自动停止播放并退出应用",
                color = Neu.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )

            if (remainingMs > 0L) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "剩余",
                        color = Neu.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatTime(remainingMs),
                        color = Neu.Accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(10.dp))
                    NeuChip(text = "取消", onClick = onCancel)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("时长（分钟）", color = Neu.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SLEEP_PRESETS.forEach { minutes ->
                    NeuChip(
                        text = "$minutes",
                        onClick = { onStart(minutes) },
                        modifier = Modifier.weight(1f),
                        accent = activeMinutes == minutes,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自定义", color = Neu.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = custom,
                    onValueChange = { input -> custom = input.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Neu.TextPrimary,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .width(84.dp)
                        .background(Neu.Background, NeuShapes.pill)
                        .neuInset(NeuShapes.pill, stroke = 2.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (custom.isEmpty()) {
                                Text(
                                    text = "分钟",
                                    color = Neu.TextSecondary.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                NeuChip(
                    text = "开始",
                    onClick = { customMinutes?.let(onStart) },
                    enabled = customValid,
                    accent = true,
                )
            }
        }
    }
}
