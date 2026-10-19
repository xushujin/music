package com.azhan.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
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

/** 删除单曲文件的结果。 */
sealed interface DeleteResult {
    /**
     * @param fileMissing 文件本来就不在了（比如在别的 App 里删过），
     *                    这时只把曲库里的记录清掉，不需要报错吓用户。
     */
    data class Deleted(val title: String, val fileMissing: Boolean = false) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

data class ScanState(val playlistName: String, val currentDir: String, val found: Int)

/** 「重新扫描全部目录」的结果，用于给用户一句完整的反馈。 */
data class RescanSummary(val playlists: Int, val songs: Int, val failed: Int)

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

    /**
     * 只从列表移除、文件还留在手机里的歌曲 id。
     * 重新扫描目录时用它过滤，否则被移除的歌下次扫描又会冒出来。
     */
    private val _removed = MutableStateFlow<Set<String>>(emptySet())
    val removed: StateFlow<Set<String>> = _removed.asStateFlow()

    /** 全局偏好（设置页）。 */
    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    suspend fun load() {
        _playlists.value = store.playlists.first()
        _favorites.value = store.favorites.first()
        _removed.value = store.removedSongs.first()
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
        } catch (c: CancellationException) {
            throw c
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
        } catch (c: CancellationException) {
            throw c
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
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /**
     * 删除一首歌对应的**本地文件**，并从曲库 / 收藏里移除。
     *
     * 走 SAF 的 `DocumentFile.delete()`，需要目录的写权限：
     * - 新添加的目录会同时申请读写（见 HomeScreen），删除开箱可用；
     * - 早期版本只申请了读权限的目录，这里会返回一条明确的提示，
     *   让用户移除目录后重新添加 —— tree uri 不变，收藏和歌词微调都不会丢。
     *
     * 文件已经不存在的（在别的 App 里删过）不算失败，直接清掉曲库记录。
     */
    suspend fun deleteSong(songId: String): DeleteResult = withContext(Dispatchers.IO) {
        val song = song(songId) ?: return@withContext DeleteResult.Failed("这首歌已经不在曲库里了")

        // 幂等：已经有读写授权时什么都不会发生；只有读权限的旧目录会抛 SecurityException，
        // 这里吞掉，真正的失败会在 delete() 时以更具体的文案报出来。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(song.treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        val document = runCatching { DocumentFile.fromSingleUri(context, Uri.parse(song.uri)) }.getOrNull()
        if (document == null || !document.exists()) {
            removeSongFromLibrary(song)
            return@withContext DeleteResult.Deleted(song.title, fileMissing = true)
        }

        val deleted = try {
            document.delete()
        } catch (e: SecurityException) {
            return@withContext DeleteResult.Failed(
                "没有删除权限。请回首页移除这个目录后重新添加（重新授权会同时申请读写）"
            )
        } catch (t: Throwable) {
            return@withContext DeleteResult.Failed(t.message ?: "删除失败")
        }

        if (!deleted) {
            return@withContext DeleteResult.Failed("系统拒绝删除这个文件（可能被其他 App 占用或只读）")
        }

        removeSongFromLibrary(song)
        DeleteResult.Deleted(song.title)
    }

    /**
     * 只把歌从播放列表移除，**不动文件**。
     *
     * 会记住这个 id：重新扫描目录时按它过滤，被移除的歌不会又冒出来。
     * 返回被移除的歌，供 Snackbar 的「撤销」用；曲库里找不到时返回 null。
     */
    suspend fun removeFromPlaylist(songId: String): Song? = withContext(Dispatchers.IO) {
        val song = song(songId) ?: return@withContext null
        val nextRemoved = _removed.value + song.id
        _removed.value = nextRemoved
        store.saveRemovedSongs(nextRemoved)
        dropSongFromLists(song)
        song
    }

    /**
     * 撤销「仅移除」：把歌放回它原来的播放列表，并取消隐藏标记。
     * 用的是内存里保存的 Song 数据，不需要重新扫描目录。
     */
    suspend fun restoreSong(song: Song) = withContext(Dispatchers.IO) {
        if (_removed.value.contains(song.id)) {
            val nextRemoved = _removed.value - song.id
            _removed.value = nextRemoved
            store.saveRemovedSongs(nextRemoved)
        }

        val collator = Collator.getInstance(Locale.CHINA)
        val now = System.currentTimeMillis()
        val next = _playlists.value.map { playlist ->
            if (playlist.id != song.treeUri) {
                playlist
            } else {
                playlist.copy(
                    songs = (playlist.songs + song)
                        .distinctBy { it.id }
                        .sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) }),
                    updatedAt = now,
                )
            }
        }
        persist(next)
    }

    /** 从播放列表里摘掉这首歌并落盘（不碰文件，也不碰收藏）。 */
    private suspend fun dropSongFromLists(song: Song) {
        val now = System.currentTimeMillis()
        val next = _playlists.value.map { playlist ->
            if (playlist.songs.none { it.id == song.id }) {
                playlist
            } else {
                playlist.copy(
                    songs = playlist.songs.filterNot { it.id == song.id },
                    updatedAt = now,
                )
            }
        }
        persist(next)
    }

    /** 文件删掉后同步清理曲库与收藏，避免留下点不开的死条目。 */
    private suspend fun removeSongFromLibrary(song: Song) {
        dropSongFromLists(song)

        // 物理删除后文件不会再被扫到，隐藏标记留着也没意义
        if (_removed.value.contains(song.id)) {
            val nextRemoved = _removed.value - song.id
            _removed.value = nextRemoved
            store.saveRemovedSongs(nextRemoved)
        }

        if (_favorites.value.contains(song.id)) {
            val left = _favorites.value - song.id
            _favorites.value = left
            store.saveFavorites(left)
        }
    }

    /**
     * 跨目录**全局**搜索：按显示名匹配所有目录里的歌，并带上它所属的目录名。
     * 空查询返回空列表（不返回全库，免得误触发一个巨大的列表）。
     */
    fun searchSongs(query: String): List<SongHit> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val collator = Collator.getInstance(Locale.CHINA)
        return _playlists.value
            .asSequence()
            .flatMap { playlist ->
                playlist.songs.asSequence()
                    .filter { it.title.contains(keyword, ignoreCase = true) }
                    .map { SongHit(it, playlist.name) }
            }
            .distinctBy { it.song.id }
            .sortedWith(Comparator { a, b -> collator.compare(a.song.title, b.song.title) })
            .toList()
    }

    /** 曲库里的歌曲总数，搜索页空态提示用。 */
    fun totalSongCount(): Int = _playlists.value.sumOf { it.songs.size }

    /** 重新扫描所有目录，返回扫描摘要（成功目录数 / 歌曲总数 / 失败目录数）。 */
    suspend fun rescanAll(): RescanSummary {
        val all = _playlists.value.toList()
        var songs = 0
        var failed = 0
        all.forEach { playlist ->
            when (val result = rescan(playlist.id)) {
                is AddResult.Rescanned -> songs += result.count
                is AddResult.Added -> songs += result.count
                is AddResult.Failed -> failed++
            }
        }
        return RescanSummary(playlists = all.size, songs = songs, failed = failed)
    }

    /** 清空收藏，返回实际清掉的歌曲数（已失效的收藏不算）。 */
    suspend fun clearFavorites(): Int {
        val count = favoriteSongs().size
        if (_favorites.value.isEmpty()) return 0
        _favorites.value = emptySet()
        withContext(Dispatchers.IO) { store.saveFavorites(emptySet()) }
        return count
    }

    private suspend fun scanTree(playlistId: String, treeUri: Uri, name: String): List<Song> {
        _scanning.value = ScanState(name, "", 0)
        val raws = scanner.scan(treeUri) { dir, found ->
            _scanning.value = ScanState(name, dir, found)
        }
        _scanning.value = ScanState(name, "", raws.size)

        // 不读音频标签：标题直接用文件名（去扩展名），艺术家/专辑/时长都不读。
        // 时长由播放器在播放时实时提供，扫描阶段不预读，避免逐个开 MediaMetadataRetriever。
        // 同时过滤掉「只从列表移除」的歌，否则每次重新扫描都会把它们放回来。
        val hidden = _removed.value
        val collator = Collator.getInstance(Locale.CHINA)
        return raws
            .distinctBy { it.uri.toString() }
            .filterNot { it.uri.toString() in hidden }
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
