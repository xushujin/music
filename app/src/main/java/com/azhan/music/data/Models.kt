package com.azhan.music.data

import kotlinx.serialization.Serializable

/**
 * 一首歌。所有路径都用 SAF 的 content URI 字符串表示，避免申请存储权限。
 *
 * @param id        稳定标识，等于音频文件的 document uri
 * @param treeUri   所属的目录树 uri（即 playlist id），用于反查歌词
 * @param relPath   相对目录树根的路径，形如 "专辑/song.mp3"，用于反查歌词
 * @param lrcUri    扫描时配到的同名 .lrc（可能为 null）
 */
@Serializable
data class Song(
    val id: String,
    val uri: String,
    val treeUri: String = "",
    val relPath: String = "",
    val fileName: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val modified: Long = 0L,
    val lrcUri: String? = null,
) {
    val baseName: String get() = fileName.substringBeforeLast('.')
    val displayArtist: String get() = artist?.takeIf { it.isNotBlank() } ?: "未知艺术家"
}

/** 一个播放列表 = 一个用户添加的目录。 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val treeUri: String,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val songs: List<Song> = emptyList(),
)

/** 歌词相关的用户偏好：颜色、字号、每首歌的时间微调。 */
@Serializable
data class LyricPrefs(
    val color: Long = 0xFFFFD54F,
    val fontSize: Float = 22f,
    val offsets: Map<String, Long> = emptyMap(),
)

/**
 * 全局搜索命中的一条：歌 + 它来自哪个目录。
 * 不落盘，所以不需要 @Serializable。
 */
data class SongHit(val song: Song, val playlistName: String)

/** 定时退出倒计时到点后的动作。 */
@Serializable
enum class SleepAction { EXIT, PAUSE }

/** 外观：跟随系统 / 固定浅色 / 固定深色。 */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 全局偏好（设置页）。
 *
 * @param sleepAction 倒计时到点后是退出应用还是只暂停
 * @param autoResume  启动时是否把上次播放的歌装回播放器（只装载、不自动播放）
 * @param lastSongId  上次播放的歌曲 id，仅用于上面的恢复
 * @param themeMode   外观。默认跟随系统；浅色/深色是两套独立的拟态配色，不是简单反色
 */
@Serializable
data class AppPrefs(
    val sleepAction: SleepAction = SleepAction.EXIT,
    val autoResume: Boolean = true,
    val lastSongId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
