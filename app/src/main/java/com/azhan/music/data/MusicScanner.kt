package com.azhan.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用 SAF 递归遍历用户选中的目录，收集音频文件，并在同一目录内配对同名 .lrc。
 *
 * 只在「添加目录 / 手动重扫」时跑，结果落盘，所以冷启动不扫描。
 */
class MusicScanner(private val context: Context) {

    data class RawSong(
        val uri: Uri,
        val name: String,
        val size: Long,
        val modified: Long,
        val relPath: String,
        val lrcUri: Uri?,
    )

    /**
     * @param onDir 每进入一个目录回调一次（目录相对路径, 当前已找到的歌曲数）。
     *              带计数是为了让设置页能显示真实进度，而不是一直停在 0。
     */
    suspend fun scan(treeUri: Uri, onDir: (String, Int) -> Unit = { _, _ -> }): List<RawSong> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val out = ArrayList<RawSong>()
            walk(root, "", 0, out, onDir)
            out
        }

    private fun walk(
        dir: DocumentFile,
        path: String,
        depth: Int,
        out: MutableList<RawSong>,
        onDir: (String, Int) -> Unit,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        onDir(if (path.isEmpty()) dir.name.orEmpty() else path, out.size)

        // 先收集本目录下的歌词，再和音频配对
        val lyrics = HashMap<String, Uri>()
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory || name.startsWith(".")) continue
            val dot = name.lastIndexOf('.')
            if (dot <= 0) continue
            if (name.substring(dot + 1).equals("lrc", ignoreCase = true)) {
                lyrics[name.substring(0, dot).lowercase()] = child.uri
            }
        }

        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, if (path.isEmpty()) name else "$path/$name", depth + 1, out, onDir)
                if (out.size >= MAX_FILES) return
                continue
            }
            val dot = name.lastIndexOf('.')
            if (dot <= 0) continue
            val ext = name.substring(dot + 1).lowercase()
            if (ext !in AUDIO_EXT) continue

            val base = name.substring(0, dot).lowercase()
            out.add(
                RawSong(
                    uri = child.uri,
                    name = name,
                    size = child.length(),
                    modified = child.lastModified(),
                    relPath = if (path.isEmpty()) name else "$path/$name",
                    lrcUri = lyrics[base] ?: lyrics[name.lowercase()],
                )
            )
            if (out.size >= MAX_FILES) return
        }
    }

    companion object {
        val AUDIO_EXT = setOf(
            "mp3", "m4a", "mp4a", "aac", "flac", "wav", "wave", "ogg", "oga",
            "opus", "wma", "ape", "aif", "aiff", "mka", "amr", "3gp", "dsf", "dff",
        )

        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 6000
    }
}
