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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azhan.music.data.Song
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuIconButton
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset

/**
 * 歌曲行。播放列表页和收藏页共用。
 *
 * @param trailing 尾部控件插槽（收藏红心之类）。为 null 时不留空位。
 */
@Composable
fun SongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
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
            // end 留窄一点，给尾部按钮腾位置
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
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
        if (song.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatTime(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Neu.TextSecondary,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(4.dp))
            it()
        }
    }
}

/**
 * 收藏红心：已收藏=实心强调色，未收藏=空心次要色。
 * 注意 `onClick` 放在最后 —— 这样调用处可以写成 `FavoriteButton(isFavorite) { ... }`。
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    size: Dp = 38.dp,
    onClick: () -> Unit,
) {
    NeuIconButton(
        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        onClick = onClick,
        contentDescription = if (isFavorite) "取消收藏" else "收藏",
        size = size,
        iconSize = 18.dp,
        tint = if (isFavorite) Neu.Accent else Neu.TextSecondary,
    )
}
