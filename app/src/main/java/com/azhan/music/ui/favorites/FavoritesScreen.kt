package com.azhan.music.ui.favorites

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.ui.common.FavoriteButton
import com.azhan.music.ui.common.MiniPlayerBar
import com.azhan.music.ui.common.SongRow
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    app: MusicApp,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = app.player
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val favorites by app.library.favorites.collectAsStateWithLifecycle()
    // 收藏集合或曲库变化时重算（曲库变了，被移除的歌要自动消失）
    val songs = remember(playlists, favorites) { app.library.favoriteSongs() }

    Scaffold(containerColor = Neu.Background) { padding ->
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
                        text = "收藏",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Neu.TextPrimary,
                    )
                    Text(
                        text = "${songs.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neu.TextSecondary,
                    )
                }
            }

            if (songs.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Neu.Background, NeuShapes.pill)
                                .neuInset(NeuShapes.pill, stroke = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Neu.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = "还没有收藏的歌",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Neu.TextPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "在播放页或歌曲列表里点一下红心，歌就会出现在这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neu.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        val isCurrent = song.id == player.currentSongId
                        SongRow(
                            song = song,
                            index = index,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && player.isPlaying,
                            onClick = {
                                // 用收藏列表本身作为播放队列
                                player.play(songs, index)
                                onOpenPlayer()
                            },
                            trailing = {
                                FavoriteButton(isFavorite = true) {
                                    scope.launch { app.library.toggleFavorite(song.id) }
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
