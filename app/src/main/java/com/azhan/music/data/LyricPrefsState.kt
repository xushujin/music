package com.azhan.music.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 歌词偏好。颜色/字号是全局的，时间微调是按歌曲的（每首歌节奏不同，偏移量不能共用）。
 * 用 Compose 的 snapshot state 暴露，UI 直接读就会自动重组。
 */
class LyricPrefsState(private val store: MusicStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var color by mutableStateOf(DEFAULT_COLOR)
        private set

    var fontSize by mutableStateOf(DEFAULT_FONT_SIZE)
        private set

    private var offsets by mutableStateOf<Map<String, Long>>(emptyMap())

    private var saveJob: Job? = null

    fun load() {
        scope.launch {
            val prefs = store.lyricPrefs.first()
            color = prefs.color
            fontSize = prefs.fontSize
            offsets = prefs.offsets
        }
    }

    fun offsetOf(songId: String?): Long =
        if (songId == null) 0L else offsets[songId] ?: 0L

    /** deltaMs > 0 表示歌词提前出现（有效播放位置 = 真实位置 + offset）。 */
    fun addOffset(songId: String, deltaMs: Long) {
        val next = (offsetOf(songId) + deltaMs).coerceIn(-30_000L, 30_000L)
        offsets = offsets + (songId to next)
        save()
    }

    fun resetOffset(songId: String) {
        offsets = offsets - songId
        save()
    }

    fun updateColor(value: Long) {
        color = value
        saveDebounced()
    }

    fun updateFontSize(value: Float) {
        fontSize = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        saveDebounced()
    }

    /** 拖色条/连续点字号会产生大量写入，合并成一次落盘。 */
    private fun saveDebounced() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            persistNow()
        }
    }

    private fun save() {
        persistNow()
    }

    private fun persistNow() {
        val c = color
        val f = fontSize
        val o = offsets
        scope.launch { store.saveLyricPrefs(LyricPrefs(color = c, fontSize = f, offsets = o)) }
    }

    companion object {
        const val DEFAULT_COLOR = 0xFFFFD54FL
        const val DEFAULT_FONT_SIZE = 22f
        const val MIN_FONT_SIZE = 14f
        const val MAX_FONT_SIZE = 44f
    }
}
