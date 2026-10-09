package com.azhan.music.lyrics

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.azhan.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌词读取 + 内存缓存。
 *
 * 优先用扫描时已经配好的 lrcUri；如果没有（比如用户后补了歌词文件），
 * 就顺着 relPath 逐级 findFile 定位到歌曲所在目录，再找同名 .lrc。
 * 只查一层目录，不做整树遍历，所以很快。
 */
class LyricsRepository(private val context: Context) {

    private val cache = LruCache<String, Lyrics>(64)

    suspend fun lyrics(song: Song?, forceReload: Boolean = false): Lyrics = withContext(Dispatchers.IO) {
        if (song == null) return@withContext Lyrics.EMPTY
        if (forceReload) cache.remove(song.id)
        cache.get(song.id)?.let { return@withContext it }

        val uri = song.lrcUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: findLrc(song)
        val parsed = uri?.let { readAndParse(it) } ?: Lyrics.EMPTY
        cache.put(song.id, parsed)
        parsed
    }

    fun invalidate(songId: String) {
        cache.remove(songId)
    }

    private fun readAndParse(uri: Uri): Lyrics = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            LrcParser.parse(TextDecoder.decode(input.readBytes()))
        } ?: Lyrics.EMPTY
    }.getOrDefault(Lyrics.EMPTY)

    private fun findLrc(song: Song): Uri? {
        if (song.treeUri.isBlank() || song.relPath.isBlank()) return null
        val treeUri = runCatching { Uri.parse(song.treeUri) }.getOrNull() ?: return null

        val segments = song.relPath.split('/').filter { it.isNotEmpty() }
        var dir: DocumentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        for (segment in segments.dropLast(1)) {
            dir = runCatching { dir.findFile(segment) }.getOrNull() ?: return null
            if (!dir.isDirectory) return null
        }

        val target = song.baseName.lowercase()
        val fileName = song.fileName.lowercase()
        return runCatching {
            dir.listFiles().firstOrNull { file ->
                val name = file.name ?: return@firstOrNull false
                if (file.isDirectory) return@firstOrNull false
                val dot = name.lastIndexOf('.')
                if (dot <= 0) return@firstOrNull false
                if (!name.substring(dot + 1).equals("lrc", ignoreCase = true)) {
                    return@firstOrNull false
                }
                val base = name.substring(0, dot).lowercase()
                base == target || base == fileName
            }?.uri
        }.getOrNull()
    }
}
