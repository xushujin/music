package com.azhan.music.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azhan.music.MusicApp
import com.azhan.music.data.Playlist
import com.azhan.music.data.Song
import com.azhan.music.lyrics.Lyrics
import java.util.Locale

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun List<Playlist>.findSong(songId: String?): Song? {
    if (songId == null) return null
    for (playlist in this) {
        playlist.songs.firstOrNull { it.id == songId }?.let { return it }
    }
    return null
}

/**
 * 按当前歌曲加载歌词。reloadKey 变化时强制重读（用于用户后补歌词文件的场景）。
 */
@Composable
fun rememberLyrics(app: MusicApp, song: Song?, reloadKey: Int = 0): Lyrics {
    var lyrics by remember(song?.id) { mutableStateOf(Lyrics.EMPTY) }
    LaunchedEffect(song?.id, reloadKey) {
        lyrics = app.lyrics.lyrics(song, forceReload = reloadKey > 0)
    }
    return lyrics
}
