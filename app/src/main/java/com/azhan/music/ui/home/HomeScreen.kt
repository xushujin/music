package com.azhan.music.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.data.AddResult
import com.azhan.music.data.Playlist
import com.azhan.music.data.ScanState
import com.azhan.music.ui.common.MiniPlayerBar
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuButton
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    app: MusicApp,
    onOpenPlaylist: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    // 用「能解析出来的歌」计数，而不是收藏 id 数——目录被移除后 id 会失效
    val favoriteCount = remember(playlists, favorites) { app.library.favoriteSongs().size }
    val snackbarHostState = remember { SnackbarHostState() }

    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 关键：拿到持久化权限，否则重启后就读不到这个目录了。
            // 读写一起申请 —— 播放列表里的「删除文件」需要写权限；个别 provider 不给写时退回只读。
            val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, readWrite)
            }.isSuccess
            if (!granted) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            scope.launch {
                snackbarHostState.showSnackbar(app.library.addDirectory(uri).toMessage())
            }
        }
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
            // 顶部：标题 + 添加目录
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的音乐",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Neu.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                NeuIconButton(
                    icon = Icons.Filled.Search,
                    onClick = onOpenSearch,
                    contentDescription = "搜索全部目录",
                    tint = Neu.TextSecondary,
                )
                Spacer(Modifier.width(6.dp))
                NeuIconButton(
                    icon = Icons.Filled.CreateNewFolder,
                    onClick = { directoryPicker.launch(null) },
                    contentDescription = "添加音乐目录",
                    tint = Neu.Accent,
                )
                Spacer(Modifier.width(6.dp))
                NeuIconButton(
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    contentDescription = "设置",
                    tint = Neu.TextSecondary,
                )
            }

            Box(Modifier.weight(1f)) {
                if (playlists.isEmpty()) {
                    EmptyLibrary(
                        onAdd = { directoryPicker.launch(null) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "favorites") {
                            FavoritesCard(count = favoriteCount, onClick = onOpenFavorites)
                        }
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                                onRescan = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            app.library.rescan(playlist.id).toMessage()
                                        )
                                    }
                                },
                                onRemove = {
                                    scope.launch {
                                        app.library.remove(playlist.id)
                                        snackbarHostState.showSnackbar("已移除「${playlist.name}」")
                                    }
                                },
                            )
                        }
                    }
                }
                scanning?.let { state ->
                    ScanBanner(state = state, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }

            MiniPlayerBar(app = app, onClick = onOpenPlayer)
        }
    }
}

private fun AddResult.toMessage(): String = when (this) {
    is AddResult.Added -> "已添加「$name」，共 $count 首"
    is AddResult.Rescanned -> "已更新「$name」，共 $count 首"
    is AddResult.Failed -> "失败：$message"
}

/** 首页顶部的「收藏」入口，样式与目录卡片一致，只是没有右侧菜单。 */
@Composable
private fun FavoritesCard(count: Int, onClick: () -> Unit) {
    NeuBox(
        modifier = Modifier.fillMaxWidth(),
        shape = NeuShapes.card,
        color = Neu.Background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Neu.Background, NeuShapes.pill)
                    .neuInset(NeuShapes.pill, stroke = 2.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Neu.Accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "收藏",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Neu.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$count 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onRescan: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    NeuBox(
        modifier = Modifier.fillMaxWidth(),
        shape = NeuShapes.card,
        color = Neu.Background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 凹陷圆形图标槽
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Neu.Background, NeuShapes.pill)
                    .neuInset(NeuShapes.pill, stroke = 2.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = Neu.Accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Neu.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${playlist.songs.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                )
            }

            Box {
                NeuIconButton(
                    icon = Icons.Filled.MoreVert,
                    onClick = { menuOpen = true },
                    contentDescription = "更多操作",
                    size = 40.dp,
                    iconSize = 18.dp,
                    tint = Neu.TextSecondary,
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Neu.Background,
                ) {
                    DropdownMenuItem(
                        text = { Text("重新扫描", color = Neu.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Neu.TextSecondary)
                        },
                        onClick = { menuOpen = false; onRescan() },
                    )
                    DropdownMenuItem(
                        text = { Text("移除目录", color = Neu.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = Neu.TextSecondary)
                        },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(Neu.Background, NeuShapes.pill)
                .neuInset(NeuShapes.pill, stroke = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = Neu.TextSecondary,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "还没有音乐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Neu.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "添加一个音乐目录，App 会按目录名自动建一个播放列表。\n目录里的同名 .lrc 会被识别为歌词。",
            style = MaterialTheme.typography.bodyMedium,
            color = Neu.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        NeuButton(onClick = onAdd) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Neu.Accent,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "添加音乐目录",
                color = Neu.Accent,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ScanBanner(state: ScanState, modifier: Modifier = Modifier) {
    NeuBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        color = Neu.Background,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = "正在扫描「${state.playlistName}」",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Neu.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.currentDir.ifBlank { "已找到 ${state.found} 个音频文件" },
                style = MaterialTheme.typography.bodySmall,
                color = Neu.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            // 凹陷进度槽 + 不确定态滑块
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Neu.Background, NeuShapes.pill)
                    .neuInset(NeuShapes.pill, stroke = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Neu.Accent,
                    trackColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            }
        }
    }
}
