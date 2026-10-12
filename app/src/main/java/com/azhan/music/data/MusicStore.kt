package com.azhan.music.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "light_music")

/**
 * 极简持久化：DataStore + JSON。
 * 拆成三个 key 是为了让高频写入（歌词微调）不会连带重写整个曲库。
 */
class MusicStore(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val playlistsKey = stringPreferencesKey("playlists")
    private val lyricKey = stringPreferencesKey("lyric_prefs")
    private val favoritesKey = stringPreferencesKey("favorites")

    private val playlistsSerializer = ListSerializer(Playlist.serializer())
    private val favoritesSerializer = ListSerializer(String.serializer())

    val playlists: Flow<List<Playlist>> = appContext.dataStore.data.map { prefs ->
        prefs[playlistsKey]?.let {
            runCatching { json.decodeFromString(playlistsSerializer, it) }.getOrNull()
        } ?: emptyList()
    }

    val lyricPrefs: Flow<LyricPrefs> = appContext.dataStore.data.map { prefs ->
        prefs[lyricKey]?.let {
            runCatching { json.decodeFromString(LyricPrefs.serializer(), it) }.getOrNull()
        } ?: LyricPrefs()
    }

    /** 收藏的歌曲 id 集合。用 Set 语义（无序、去重），落盘时转成列表。 */
    val favorites: Flow<Set<String>> = appContext.dataStore.data.map { prefs ->
        prefs[favoritesKey]?.let {
            runCatching { json.decodeFromString(favoritesSerializer, it) }.getOrNull()
        }?.toSet() ?: emptySet()
    }

    suspend fun savePlaylists(list: List<Playlist>) {
        appContext.dataStore.edit { it[playlistsKey] = json.encodeToString(playlistsSerializer, list) }
    }

    suspend fun saveFavorites(ids: Set<String>) {
        appContext.dataStore.edit {
            it[favoritesKey] = json.encodeToString(favoritesSerializer, ids.toList())
        }
    }

    suspend fun saveLyricPrefs(prefs: LyricPrefs) {
        appContext.dataStore.edit { it[lyricKey] = json.encodeToString(LyricPrefs.serializer(), prefs) }
    }
}
