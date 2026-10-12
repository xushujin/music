package com.azhan.music.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.azhan.music.data.SleepAction
import com.azhan.music.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放控制器：连到 PlaybackService 的 MediaController，把播放状态镜像成 Compose state，
 * UI 直接读字段即可，不需要额外的 ViewModel / Flow 中转。
 */
@SuppressLint("UnsafeOptInUsageError", "UnstableApiUsage")
class PlaybackController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    private var pendingAction: (() -> Unit)? = null

    var controller: MediaController? = null
        private set

    var connected by mutableStateOf(false)
        private set

    var currentSongId by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set

    var shuffleEnabled by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 定时退出：剩余毫秒，0 表示未启用。 */
    var sleepRemainingMs by mutableLongStateOf(0L)
        private set

    /** 定时退出：本次设定的总分钟数（用于在预设里高亮当前选项），0 表示未启用。 */
    var sleepTotalMinutes by mutableIntStateOf(0)
        private set

    /**
     * 定时器到点后的回调，在主线程调用。MainActivity 用它来关掉界面。
     * 放在这里而不是 UI 层，是因为定时器必须在界面不可见时也能可靠触发。
     */
    var onSleepTimerEnd: (() -> Unit)? = null

    /** 倒计时到点后的动作，由设置页决定（默认退出应用）。 */
    var sleepAction: SleepAction = SleepAction.EXIT

    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlaybackController.isPlaying = isPlaying
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentSongId = mediaItem?.mediaId
            positionMs = 0L
            durationMs = controller?.duration?.takeIf { it > 0 } ?: 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = controller?.duration ?: 0L
            if (duration > 0) durationMs = duration
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlaybackController.repeatMode = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            this@PlaybackController.shuffleEnabled = shuffleModeEnabled
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = error.message ?: "播放出错"
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    mediaController.addListener(listener)
                    controller = mediaController
                    connected = true
                    syncFrom(mediaController)
                    val action = pendingAction
                    pendingAction = null
                    action?.invoke()
                } catch (t: Throwable) {
                    errorMessage = t.message ?: "播放服务连接失败"
                    controllerFuture = null
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        cancelSleepTimer()
        stopTicker()
        controller?.removeListener(listener)
        controller = null
        connected = false
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            connect()
            pendingAction = { play(songs, startIndex) }
            return
        }
        val items = songs.map { it.toMediaItem() }
        mediaController.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        mediaController.prepare()
        mediaController.play()
    }

    /**
     * 只装载队列、不开始播放（供「启动时恢复上次播放」用）。
     * 与 [play] 的唯一区别是最后不调 play()，所以不会一启动就出声。
     */
    fun prepare(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            connect()
            pendingAction = { prepare(songs, startIndex) }
            return
        }
        val items = songs.map { it.toMediaItem() }
        mediaController.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        mediaController.prepare()
    }

    fun toggle() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
            mediaController.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val mediaController = controller ?: return
        // 播了 3 秒以上，先回到本曲开头，符合主流播放器习惯
        if (mediaController.currentPosition > 3_000) mediaController.seekTo(0)
        else mediaController.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        controller?.seekTo(target)
        positionMs = target
    }

    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0) return
        seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun cycleRepeatMode() {
        val mediaController = controller ?: return
        mediaController.repeatMode = when (mediaController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val mediaController = controller ?: return
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }

    // ---- 定时退出 ----

    /**
     * 启动倒计时，到点后暂停播放并触发 [onSleepTimerEnd]。
     * 用「绝对结束时间」算剩余，而不是累加 delay，避免长时间运行后的漂移。
     */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) return
        sleepJob?.cancel()
        sleepTotalMinutes = minutes
        sleepRemainingMs = minutes * 60_000L
        sleepJob = scope.launch {
            val endAt = System.currentTimeMillis() + minutes * 60_000L
            while (isActive) {
                val left = endAt - System.currentTimeMillis()
                sleepRemainingMs = left.coerceAtLeast(0L)
                if (left <= 0L) break
                delay(500)
            }
            sleepJob = null
            sleepTotalMinutes = 0
            runCatching { controller?.pause() } // 到点先停止播放
            // 再按设置决定：退出应用，还是只停在这里
            if (sleepAction == SleepAction.EXIT) onSleepTimerEnd?.invoke()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemainingMs = 0L
        sleepTotalMinutes = 0
    }

    private fun syncFrom(mediaController: MediaController) {
        currentSongId = mediaController.currentMediaItem?.mediaId
        isPlaying = mediaController.isPlaying
        repeatMode = mediaController.repeatMode
        shuffleEnabled = mediaController.shuffleModeEnabled
        positionMs = mediaController.currentPosition.coerceAtLeast(0L)
        durationMs = mediaController.duration.takeIf { it > 0 } ?: 0L
        if (mediaController.isPlaying) startTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val mediaController = controller ?: break
                positionMs = mediaController.currentPosition.coerceAtLeast(0L)
                val duration = mediaController.duration
                if (duration > 0 && duration != durationMs) durationMs = duration
                delay(80)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(displayArtist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
