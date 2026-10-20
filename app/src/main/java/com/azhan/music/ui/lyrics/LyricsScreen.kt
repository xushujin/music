package com.azhan.music.ui.lyrics

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.data.LyricPrefsState
import com.azhan.music.lyrics.LyricLine
import com.azhan.music.ui.common.findSong
import com.azhan.music.ui.common.rememberLyrics
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuChip
import com.azhan.music.ui.theme.NeuIconButton
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private val PRESET_COLORS = listOf(
    0xFFFFFFFFL, 0xFFFFD54FL, 0xFFFF80ABL, 0xFF80DEEAL,
    0xFF69F0AEL, 0xFFB388FFL, 0xFFFFAB40L, 0xFFFF5252L,
)

/** 白色/近灰在浅色拟态背景上不可见，用这个墨色兜底。 */
private val InkColor = Color(0xFF2B3040)

/** 深色皮肤下近白/近灰反过来要用浅色，否则贴在 #23262D 上看不见。 */
private val InkOnDark = Color(0xFFE8EBF2)

/**
 * 歌词颜色适配当前皮肤。
 *
 * 浅色：保留色相，拉高饱和、压暗明度 —— 原来那套淡色预设（白/淡黄/淡绿）在 #E7EAF0 上几乎看不见。
 * 深色：反过来，提亮明度、略降饱和，深底上才够醒目。
 * 近白/近灰（饱和度很低）没有色相可言，浅色用墨色、深色用浅灰兜底。
 * 色块也显示转换后的颜色，做到所见即所得。
 */
private fun readableColor(argb: Long, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
    return when {
        hsv[1] < 0.2f -> if (dark) InkOnDark else InkColor
        dark -> Color.hsv(hsv[0], 0.65f, 1f)
        else -> Color.hsv(hsv[0], 0.88f, 0.5f)
    }
}

@Composable
fun LyricsScreen(app: MusicApp, onBack: () -> Unit) {
    val player = app.player
    val prefs = app.lyricPrefs
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val song = remember(playlists, player.currentSongId) { playlists.findSong(player.currentSongId) }
    val songId = song?.id

    // 初值给 1：每次进全屏都强制重读 .lrc，这样在电脑上改完歌词回来就能看到。
    // 顶栏因此不再放「刷新」按钮——它平时点了没变化，看着像坏了。
    // （播放页走的仍是缓存，不受影响。）
    var reloadKey by remember { mutableIntStateOf(1) }
    val lyrics = rememberLyrics(app, song, reloadKey)
    val offset = prefs.offsetOf(songId)

    val listState = rememberLazyListState()

    // 进入全屏歌词时不自动弹出调整窗，由用户点右上角「调整」或点空白处唤起
    var panelVisible by remember { mutableStateOf(false) }

    // 当前行：依赖 positionMs + 用户偏移。只在行号变化时才触发重组
    val currentIndexState = remember(lyrics, songId) {
        derivedStateOf { lyrics.indexAt(player.positionMs + prefs.offsetOf(songId)) }
    }
    val currentIndex by currentIndexState

    val userScrollUntil = remember { mutableLongStateOf(0L) }
    val programmaticScroll = remember { mutableStateOf(false) }

    // 全屏：隐藏系统栏 + 屏幕常亮
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    // 用户手动滑动时，暂停自动居中 2.5 秒
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmaticScroll.value) {
                userScrollUntil.longValue = System.currentTimeMillis() + 2500L
            }
        }
    }

    // 当前行居中
    LaunchedEffect(lyrics, songId) {
        var lastCentered = -1
        while (true) {
            val index = currentIndexState.value
            val idle = System.currentTimeMillis() >= userScrollUntil.longValue
            if (index >= 0 && index != lastCentered && idle) {
                programmaticScroll.value = true
                runCatching { listState.centerOn(index) }
                programmaticScroll.value = false
                lastCentered = index
            }
            delay(150)
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val lyricColor = readableColor(prefs.color, Neu.palette.isDark)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Neu.Background)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { panelVisible = !panelVisible },
    ) {
        if (song == null) {
            Text(
                text = "还没有在播放的歌曲",
                color = Neu.TextSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (lyrics.isEmpty) {
            NoLyricsHint(
                fileName = song.fileName,
                onReload = { reloadKey++ },
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // 歌词区从顶部按钮栏下方才开始，滚动时不会钻到返回键/标题底下。
                    // 顶部栏是后绘制的浮层且没有底衬，不预留这段就会和歌词字叠在一起。
                    .padding(top = 72.dp),
                // 视口被上面截掉一截，留白要略大于「半个视口」才能保证首尾行也能居中
                contentPadding = PaddingValues(vertical = screenHeight * 0.45f),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    LyricLineRow(
                        line = line,
                        isCurrent = index == currentIndex,
                        color = lyricColor,
                        fontSize = prefs.fontSize,
                        onClick = { player.seekTo(line.timeMs - offset) },
                    )
                }
            }
        }

        // 顶部栏
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "返回",
                size = 44.dp,
                iconSize = 20.dp,
                tint = Neu.TextPrimary,
                color = Neu.Background,
                highlight = Neu.Highlight,
                shadow = Neu.Shadow,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "",
                    color = Neu.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.displayArtist.orEmpty(),
                    color = Neu.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            NeuIconButton(
                icon = Icons.Filled.Tune,
                onClick = { panelVisible = !panelVisible },
                contentDescription = "歌词设置",
                size = 44.dp,
                iconSize = 19.dp,
                tint = if (panelVisible) Neu.Accent else Neu.TextPrimary,
                color = Neu.Background,
                highlight = Neu.Highlight,
                shadow = Neu.Shadow,
            )
        }

        // 底部设置面板（默认收起）
        AnimatedVisibility(
            visible = panelVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LyricControlPanel(
                offset = offset,
                color = prefs.color,
                fontSize = prefs.fontSize,
                onOffset = { delta -> songId?.let { prefs.addOffset(it, delta) } },
                onResetOffset = { songId?.let { prefs.resetOffset(it) } },
                onColor = { prefs.updateColor(it) },
                onFontSize = { prefs.updateFontSize(it) },
                onClose = { panelVisible = false },
            )
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isCurrent: Boolean,
    color: Color,
    fontSize: Float,
    onClick: () -> Unit,
) {
    val emphasis by animateFloatAsState(if (isCurrent) 1f else 0f, label = "lyricEmphasis")
    val textColor = color.copy(alpha = 0.45f + 0.55f * emphasis)
    val size = fontSize * (0.88f + 0.20f * emphasis)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            color = textColor,
            fontSize = size.sp,
            lineHeight = (size * 1.35f).sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        line.translation?.let { translation ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = translation,
                color = textColor.copy(alpha = textColor.alpha * 0.72f),
                fontSize = (size * 0.78f).sp,
                lineHeight = (size * 0.78f * 1.3f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LyricControlPanel(
    offset: Long,
    color: Long,
    fontSize: Float,
    onOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onColor: (Long) -> Unit,
    onFontSize: (Float) -> Unit,
    onClose: () -> Unit,
) {
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
                    text = "歌词设置",
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

            Spacer(Modifier.height(14.dp))

            // ---- 时间微调 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "时间微调",
                    color = Neu.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = offsetLabel(offset),
                    color = if (offset == 0L) Neu.TextPrimary else Neu.Accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(10.dp))
                NeuChip(
                    text = "归零",
                    onClick = onResetOffset,
                    enabled = offset != 0L,
                    accent = false,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OffsetChip("-1.0", -1000L, onOffset, Modifier.weight(1f))
                OffsetChip("-0.5", -500L, onOffset, Modifier.weight(1f))
                OffsetChip("-0.1", -100L, onOffset, Modifier.weight(1f))
                OffsetChip("+0.1", 100L, onOffset, Modifier.weight(1f))
                OffsetChip("+0.5", 500L, onOffset, Modifier.weight(1f))
                OffsetChip("+1.0", 1000L, onOffset, Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "点「+」让歌词提前出现（歌词比人声慢时用），设置按歌曲分别记住",
                color = Neu.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(Modifier.height(16.dp))

            // ---- 颜色 ----
            Text("颜色", color = Neu.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PRESET_COLORS.forEach { preset ->
                    val selected = preset == color
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(readableColor(preset, Neu.palette.isDark))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) Neu.TextPrimary else Neu.Shadow,
                                shape = CircleShape,
                            )
                            .clickable { onColor(preset) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- 字号 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "字号",
                    color = Neu.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                NeuChip(
                    text = "A-",
                    onClick = { onFontSize(fontSize - 2f) },
                    enabled = fontSize > LyricPrefsState.MIN_FONT_SIZE,
                    accent = true,
                )
                Text(
                    text = fontSize.toInt().toString(),
                    color = Neu.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                NeuChip(
                    text = "A+",
                    onClick = { onFontSize(fontSize + 2f) },
                    enabled = fontSize < LyricPrefsState.MAX_FONT_SIZE,
                    accent = true,
                )
            }
        }
    }
}

@Composable
private fun OffsetChip(
    label: String,
    deltaMs: Long,
    onOffset: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuChip(
        text = label,
        onClick = { onOffset(deltaMs) },
        modifier = modifier,
        accent = false,
    )
}

@Composable
private fun NoLyricsHint(
    fileName: String?,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expected = fileName?.substringBeforeLast('.')?.plus(".lrc")
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有找到歌词",
            color = Neu.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (expected == null) {
                "把 .lrc 歌词文件放在音乐文件旁边即可"
            } else {
                "把 $expected 放在音乐文件同一个目录里，再点下面的按钮"
            },
            color = Neu.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        NeuChip(text = "重新加载歌词", onClick = onReload, accent = true)
    }
}

private fun offsetLabel(offsetMs: Long): String =
    if (offsetMs == 0L) "0.0s" else String.format(Locale.US, "%+.1fs", offsetMs / 1000f)

/** 把当前行滚到视口正中。 */
private suspend fun LazyListState.centerOn(index: Int) {
    if (index < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        animateScrollToItem(index)
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val delta = itemCenter - viewportCenter
    if (abs(delta) > 1f) animateScrollBy(delta)
}
