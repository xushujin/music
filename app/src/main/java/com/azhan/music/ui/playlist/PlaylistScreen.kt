package com.azhan.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.azhan.music.data.Song
import com.azhan.music.ui.common.MiniPlayerBar
import com.azhan.music.ui.common.formatTime
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset
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
    val playlist = remember(playlists, playlistId) { playlists.firstOrNull { it.id == playlistId } }
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRemove by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = Neu.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "这个目录里没有找到音频文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neu.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
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
                        )
                    }
                }
            }

            MiniPlayerBar(app = app, onClick = onOpenPlayer)
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    // 正在播放的那一行做成凹陷态，其他行保持平坦（列表里每个都做阴影会很重）
    val shape = NeuShapes.chip
    val decorated = if (isCurrent) {
        Modifier
            .background(Neu.Background, shape)
            .neuInset(shape, stroke = 2.5.dp)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(decorated)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Neu.Accent,
                    modifier = Modifier.size(19.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Neu.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) Neu.Accent else Neu.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.lrcUri != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "歌词",
                        style = MaterialTheme.typography.labelSmall,
                        color = Neu.Accent,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (song.durationMs > 0) {
            Text(
                text = formatTime(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Neu.TextSecondary,
            )
        }
    }
}
