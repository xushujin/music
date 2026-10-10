package com.azhan.music.ui.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.NeuSlider
import com.azhan.music.ui.theme.neuInset
import kotlinx.coroutines.delay

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Neu.Background)
                .padding(padding)
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
    }
}
