package com.azhan.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

sealed interface AddResult {
    data class Added(val name: String, val count: Int) : AddResult
    data class Rescanned(val name: String, val count: Int) : AddResult
    data class Failed(val message: String) : AddResult
}

data class ScanState(val playlistName: String, val currentDir: String, val found: Int)

/**
 * 曲库仓库。整个 App 只有一个实例（挂在 Application 上），UI 直接读它的 StateFlow。
 */
class LibraryRepository(
    private val context: Context,
    private val store: MusicStore,
) {

    private val scanner = MusicScanner(context)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _scanning = MutableStateFlow<ScanState?>(null)
    val scanning: StateFlow<ScanState?> = _scanning.asStateFlow()

    suspend fun load() {
        _playlists.value = store.playlists.first()
    }

    fun playlist(id: String?): Playlist? =
        if (id == null) null else _playlists.value.firstOrNull { it.id == id }

    fun song(id: String?): Song? {
        if (id == null) return null
        for (playlist in _playlists.value) {
            playlist.songs.firstOrNull { it.id == id }?.let { return it }
        }
        return null
    }

    suspend fun addDirectory(treeUri: Uri): AddResult {
        val id = treeUri.toString()
        if (_playlists.value.any { it.id == id }) return rescan(id)

        val name = DocumentFile.fromTreeUri(context, treeUri)?.name?.takeIf { it.isNotBlank() }
            ?: "未命名目录"
        return try {
            val songs = scanTree(id, treeUri, name)
            val now = System.currentTimeMillis()
            val playlist = Playlist(
                id = id,
                name = name,
                treeUri = id,
                addedAt = now,
                updatedAt = now,
                songs = songs,
            )
            persist(_playlists.value + playlist)
            AddResult.Added(name, songs.size)
        } catch (t: Throwable) {
            AddResult.Failed(t.message ?: "扫描失败")
        } finally {
            _scanning.value = null
        }
    }

    suspend fun rescan(playlistId: String): AddResult {
        val playlist = playlist(playlistId) ?: return AddResult.Failed("目录不存在")
        return try {
            val songs = scanTree(playlistId, Uri.parse(playlistId), playlist.name)
            val updated = playlist.copy(songs = songs, updatedAt = System.currentTimeMillis())
            persist(_playlists.value.map { if (it.id == playlistId) updated else it })
            AddResult.Rescanned(playlist.name, songs.size)
        } catch (t: Throwable) {
            AddResult.Failed(t.message ?: "扫描失败")
        } finally {
            _scanning.value = null
        }
    }

    suspend fun remove(playlistId: String) {
        persist(_playlists.value.filterNot { it.id == playlistId })
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(playlistId),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private suspend fun scanTree(playlistId: String, treeUri: Uri, name: String): List<Song> {
        _scanning.value = ScanState(name, "", 0)
        val raws = scanner.scan(treeUri) { dir ->
            _scanning.value = ScanState(name, dir, 0)
        }
        _scanning.value = ScanState(name, "", raws.size)

        // 不读音频标签：标题直接用文件名（去扩展名），艺术家/专辑/时长都不读。
        // 时长由播放器在播放时实时提供，扫描阶段不预读，避免逐个开 MediaMetadataRetriever。
        val collator = Collator.getInstance(Locale.CHINA)
        return raws
            .distinctBy { it.uri.toString() }
            .map { raw ->
                Song(
                    id = raw.uri.toString(),
                    uri = raw.uri.toString(),
                    treeUri = playlistId,
                    relPath = raw.relPath,
                    fileName = raw.name,
                    title = raw.name.substringBeforeLast('.'),
                    artist = null,
                    album = null,
                    durationMs = 0L,
                    size = raw.size,
                    modified = raw.modified,
                    lrcUri = raw.lrcUri?.toString(),
                )
            }
            .sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
    }

    private suspend fun persist(list: List<Playlist>) {
        _playlists.value = list
        withContext(Dispatchers.IO) { store.savePlaylists(list) }
    }
}
