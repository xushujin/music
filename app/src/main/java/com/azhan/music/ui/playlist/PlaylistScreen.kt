package com.azhan.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.data.AddResult
import com.azhan.music.data.DeleteResult
import com.azhan.music.data.Song
import com.azhan.music.ui.common.FavoriteButton
import com.azhan.music.ui.common.MiniPlayerBar
import com.azhan.music.ui.common.SongRow
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    app: MusicApp,
    playlistId: String?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = app.player
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    val prefs by app.library.prefs.collectAsStateWithLifecycle()
    val playlist = remember(playlists, playlistId) { playlists.firstOrNull { it.id == playlistId } }
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRemove by remember { mutableStateOf(false) }
    // 删除文件是两步确认：1 = 说明后果，2 = 真正的最后确认
    var deleteTarget by remember { mutableStateOf<Song?>(null) }
    var deleteStage by remember { mutableStateOf(0) }

    if (playlist == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Neu.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "目录已不存在",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neu.TextPrimary,
                )
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onBack) { Text("返回", color = Neu.Accent) }
            }
        }
        return
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Neu.Background,
            title = { Text("移除目录", color = Neu.TextPrimary) },
            text = {
                Text(
                    text = "只会从曲库移除「${playlist.name}」，不会删除你手机上的文件。",
                    color = Neu.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        app.library.remove(playlist.id)
                        snackbarHostState.showSnackbar("已移除「${playlist.name}」")
                        onBack()
                    }
                }) { Text("移除", color = Neu.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("取消", color = Neu.TextSecondary) }
            },
        )
    }

    // ---- 删除本地文件：第一步，说明清楚会发生什么 ----
    val target = deleteTarget
    if (target != null && deleteStage == 1) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null; deleteStage = 0 },
            shape = NeuShapes.card,
            containerColor = Neu.Background,
            titleContentColor = Neu.TextPrimary,
            textContentColor = Neu.TextSecondary,
            title = { Text("删除这个文件？", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "「${target.fileName}」将从手机存储中永久删除，" +
                        "同时从「${playlist.name}」里移除。\n\n" +
                        "注意：删的是真实的音频文件，不是只从列表里拿掉；" +
                        "不会进回收站，删了找不回来。",
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteStage = 2 }) {
                    Text("继续", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null; deleteStage = 0 }) {
                    Text("取消", color = Neu.TextSecondary)
                }
            },
        )
    }

    // ---- 第二步，真正的最后确认 ----
    if (target != null && deleteStage == 2) {
        AlertDialog(
            onDismissRequest = { deleteStage = 1 },
            shape = NeuShapes.card,
            containerColor = Neu.Background,
            titleContentColor = Neu.TextPrimary,
            textContentColor = Neu.TextSecondary,
            title = { Text("再次确认", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "确定要永久删除「${target.fileName}」吗？\n\n" +
                        "文件会立刻从设备上消失，无法恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val song = target
                    deleteTarget = null
                    deleteStage = 0
                    scope.launch {
                        when (val result = app.library.deleteSong(song.id)) {
                            is DeleteResult.Deleted -> {
                                // 正在播 / 队列里的这首歌要一起摘掉，否则会播到一个不存在的文件
                                player.forgetSong(song.id)
                                app.lyricPrefs.removeOffset(song.id)
                                snackbarHostState.showSnackbar(
                                    if (result.fileMissing) {
                                        "「${result.title}」的文件已不存在，已从曲库移除"
                                    } else {
                                        "已删除「${result.title}」"
                                    }
                                )
                            }

                            is DeleteResult.Failed -> snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteStage = 1 }) {
                    Text("取消", color = Neu.TextSecondary)
                }
            },
        )
    }

    Scaffold(
        containerColor = Neu.Background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = NeuShapes.chip,
                    containerColor = Neu.TextPrimary,
                    contentColor = Neu.Background,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Neu.Background)
                .padding(padding),
        ) {
            // 顶部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "返回",
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Neu.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlist.songs.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neu.TextSecondary,
                    )
                }
                NeuIconButton(
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        scope.launch {
                            val result = app.library.rescan(playlist.id)
                            snackbarHostState.showSnackbar(
                                when (result) {
                                    is AddResult.Rescanned -> "已更新，共 ${result.count} 首"
                                    is AddResult.Failed -> "失败：${result.message}"
                                    is AddResult.Added -> "已更新，共 ${result.count} 首"
                                }
                            )
                        }
                    },
                    contentDescription = "重新扫描",
                    size = 44.dp,
                    iconSize = 19.dp,
                    tint = Neu.Accent,
                )
                Spacer(Modifier.width(10.dp))
                NeuIconButton(
                    icon = Icons.Filled.Delete,
                    onClick = { confirmRemove = true },
                    contentDescription = "移除目录",
                    size = 44.dp,
                    iconSize = 19.dp,
                    tint = Neu.TextSecondary,
                )
            }

            if (playlist.songs.isEmpty()) {
                EmptyHint(
                    text = "这个目录里没有找到音频文件",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(playlist.songs, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = song.id == player.currentSongId
                        SongRow(
                            song = song,
                            index = index,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && player.isPlaying,
                            onClick = {
                                player.play(playlist.songs, index)
                                onOpenPlayer()
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 删除能力可在「设置 → 数据」里关掉；关掉后整列都不出现删除入口
                                    if (prefs.allowFileDelete) {
                                        NeuIconButton(
                                            icon = Icons.Outlined.DeleteOutline,
                                            onClick = {
                                                deleteTarget = song
                                                deleteStage = 1
                                            },
                                            contentDescription = "删除文件",
                                            size = 34.dp,
                                            iconSize = 17.dp,
                                            tint = Neu.TextSecondary,
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    FavoriteButton(isFavorite = song.id in favorites) {
                                        scope.launch { app.library.toggleFavorite(song.id) }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            MiniPlayerBar(app = app, onClick = onOpenPlayer)
        }
    }
}

/** 列表区的居中空态提示。 */
@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Neu.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

