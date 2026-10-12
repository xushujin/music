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

    /** 收藏的歌曲 id。只存 id，取歌时再去曲库里查，避免同一首歌存两份。 */
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    /** 全局偏好（设置页）。 */
    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    suspend fun load() {
        _playlists.value = store.playlists.first()
        _favorites.value = store.favorites.first()
        _prefs.value = store.appPrefs.first()
    }

    suspend fun updatePrefs(transform: (AppPrefs) -> AppPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        withContext(Dispatchers.IO) { store.saveAppPrefs(next) }
    }

    /** 记住最后播放的歌，供「启动时恢复上次播放」用。歌没变就不写盘。 */
    suspend fun rememberLastSong(songId: String) {
        if (_prefs.value.lastSongId == songId) return
        updatePrefs { it.copy(lastSongId = songId) }
    }

    fun isFavorite(songId: String?): Boolean =
        songId != null && _favorites.value.contains(songId)

    suspend fun toggleFavorite(songId: String) {
        val next = _favorites.value.toMutableSet().apply {
            if (!add(songId)) remove(songId)
        }
        _favorites.value = next
        withContext(Dispatchers.IO) { store.saveFavorites(next) }
    }

    /**
     * 收藏列表：按标题排序（和目录内一致）。
     * 只保留当前曲库里还在的歌 —— 目录被移除后，收藏里的 id 会自然失效被忽略。
     */
    fun favoriteSongs(): List<Song> {
        val ids = _favorites.value
        if (ids.isEmpty()) return emptyList()
        val collator = Collator.getInstance(Locale.CHINA)
        return _playlists.value
            .asSequence()
            .flatMap { it.songs.asSequence() }
            .filter { it.id in ids }
            .distinctBy { it.id }
            .sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
            .toList()
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

    /** 重新扫描所有目录，返回扫到的歌曲总数。 */
    suspend fun rescanAll(): Int {
        var total = 0
        _playlists.value.toList().forEach { playlist ->
            when (val result = rescan(playlist.id)) {
                is AddResult.Rescanned -> total += result.count
                is AddResult.Added -> total += result.count
                is AddResult.Failed -> Unit
            }
        }
        return total
    }

    suspend fun clearFavorites() {
        if (_favorites.value.isEmpty()) return
        _favorites.value = emptySet()
        withContext(Dispatchers.IO) { store.saveFavorites(emptySet()) }
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
