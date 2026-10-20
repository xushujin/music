package com.azhan.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 拟态皮肤：浅色 / 深色两套，都是「元素与背景同色、靠双向光晕分层」。
 *
 * 深色不是把浅色反色 —— 纯黑背景上暗影没地方画，层次会糊掉，
 * 所以深色用 #23262D 这种「比黑亮一档」的底色，高光收弱、暗影加重（见 [DarkNeuPalette]）。
 */
private fun NeuPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF10131A),
        primaryContainer = background,
        onPrimaryContainer = accent,
        secondary = accentSoft,
        onSecondary = Color(0xFF10131A),
        tertiary = accentSoft,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = background,
        onSurfaceVariant = textSecondary,
        surfaceContainer = background,
        surfaceContainerHigh = background,
        outline = textSecondary,
        outlineVariant = shadow,
        error = Color(0xFFFF7A7A),
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = background,
        onPrimaryContainer = accent,
        secondary = accentSoft,
        onSecondary = Color.White,
        tertiary = accentSoft,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = background,
        onSurfaceVariant = textSecondary,
        surfaceContainer = background,
        surfaceContainerHigh = background,
        outline = textSecondary,
        outlineVariant = shadow,
        error = Color(0xFFD9534F),
    )
}

/**
 * @param darkTheme 由设置里的「外观」决定（跟随系统 / 浅色 / 深色），默认跟随系统。
 */
@Composable
fun MusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkNeuPalette else LightNeuPalette
    val colorScheme = remember(palette) { palette.toColorScheme() }

    // 状态栏/导航栏图标颜色要跟着皮肤走：深色皮肤配浅色图标，否则白底白字看不见
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalNeu provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
