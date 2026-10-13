package com.azhan.music.ui.search

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.ui.common.FavoriteButton
import com.azhan.music.ui.common.MiniPlayerBar
import com.azhan.music.ui.common.NeuSearchBar
import com.azhan.music.ui.common.SongRow
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuIconButton
import kotlinx.coroutines.launch

/**
 * 全局搜索：一次搜遍**所有目录**里的文件名，结果里标出歌来自哪个目录。
 *
 * 和「列表内过滤」的区别：这里不分你在哪个目录，命中结果是跨目录合并后按标题排序的。
 */
@Composable
fun SearchScreen(
    app: MusicApp,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = app.player
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val hits = remember(playlists, query) { app.library.searchSongs(query) }
    // 播放队列 = 当前搜索结果（所见即队列）
    val queue = remember(hits) { hits.map { it.song } }
    val total = remember(playlists) { playlists.sumOf { it.songs.size } }

    Scaffold(containerColor = Neu.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Neu.Background)
                .padding(padding),
        ) {
            // 顶栏：返回 + 搜索框（进入即聚焦）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    contentDescription = "返回",
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                NeuSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "搜索所有目录",
                    autoFocus = true,
                )
            }

            when {
                query.isBlank() -> HintBox(
                    title = "搜遍所有目录",
                    subtitle = if (total == 0) {
                        "曲库里还没有歌，先回首页添加一个音乐目录"
                    } else {
                        "曲库共 $total 首，输入关键词按文件名搜索"
                    },
                    modifier = Modifier.weight(1f),
                )

                hits.isEmpty() -> HintBox(
                    title = "没有找到「${query.trim()}」",
                    subtitle = "换个关键词试试（只匹配文件名）",
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    Text(
                        text = "找到 ${hits.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neu.TextSecondary,
                        modifier = Modifier.padding(start = 24.dp, bottom = 2.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(hits, key = { _, hit -> hit.song.id }) { index, hit ->
                            val isCurrent = hit.song.id == player.currentSongId
                            SongRow(
                                song = hit.song,
                                index = index,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && player.isPlaying,
                                onClick = {
                                    player.play(queue, index)
                                    onOpenPlayer()
                                },
                                // 搜索结果跨目录，副标题显示来源目录比「未知艺术家」有用
                                subtitle = hit.playlistName,
                                trailing = {
                                    FavoriteButton(isFavorite = hit.song.id in favorites) {
                                        scope.launch { app.library.toggleFavorite(hit.song.id) }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            MiniPlayerBar(app = app, onClick = onOpenPlayer)
        }
    }
}

/** 搜索页的居中提示（未输入 / 无结果）。 */
@Composable
private fun HintBox(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Neu.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Neu.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
