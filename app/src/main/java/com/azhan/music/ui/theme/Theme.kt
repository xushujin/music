package com.azhan.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 拟态风格只用一套浅色配色，全 App 统一（含全屏歌词页）。
 *
 * 拟态（Neumorphism）依赖"元素与背景同色、靠双向阴影区分层次"，
 * 在深色下要么对比度不够、要么阴影糊成一团，所以这里**固定浅色**，不跟随系统深色模式。
 * 歌词颜色也跟着改成"保留色相 + 压暗明度"（见 LyricsScreen.readableColor），
 * 否则原来的淡色预设在浅灰背景上看不见。
 */
private val NeuColorScheme = lightColorScheme(
    primary = Neu.Accent,
    onPrimary = Color.White,
    primaryContainer = Neu.Background,
    onPrimaryContainer = Neu.Accent,
    secondary = Neu.AccentSoft,
    onSecondary = Color.White,
    tertiary = Neu.AccentSoft,
    background = Neu.Background,
    onBackground = Neu.TextPrimary,
    surface = Neu.Background,
    onSurface = Neu.TextPrimary,
    surfaceVariant = Neu.Background,
    onSurfaceVariant = Neu.TextSecondary,
    surfaceContainer = Neu.Background,
    surfaceContainerHigh = Neu.Background,
    outline = Neu.TextSecondary,
    outlineVariant = Neu.Shadow,
    error = Color(0xFFD9534F),
)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeuColorScheme,
        content = content,
    )
}
