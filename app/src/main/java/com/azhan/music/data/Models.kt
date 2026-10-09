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
