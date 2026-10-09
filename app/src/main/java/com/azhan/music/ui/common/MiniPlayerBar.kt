package com.azhan.music.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azhan.music.MusicApp
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuBox
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset

@Composable
fun MiniPlayerBar(app: MusicApp, onClick: () -> Unit) {
    val player = app.player
    val songId = player.currentSongId ?: return
    val playlists by app.library.playlists.collectAsStateWithLifecycle()
    val song = remember(playlists, songId) { playlists.findSong(songId) }

    NeuBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 凹陷的圆形封面位
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Neu.Background, NeuShapes.pill)
                    .neuInset(NeuShapes.pill, stroke = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Neu.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "未知曲目",
                    style = MaterialTheme.typography.titleSmall,
                    color = Neu.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.displayArtist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neu.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NeuIconButton(
                icon = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = { player.toggle() },
                contentDescription = if (player.isPlaying) "暂停" else "播放",
                size = 42.dp,
                iconSize = 20.dp,
                tint = Neu.Accent,
            )
            Spacer(Modifier.width(8.dp))
            NeuIconButton(
                icon = Icons.Filled.SkipNext,
                onClick = { player.next() },
                contentDescription = "下一首",
                size = 42.dp,
                iconSize = 20.dp,
            )
        }
    }
}
