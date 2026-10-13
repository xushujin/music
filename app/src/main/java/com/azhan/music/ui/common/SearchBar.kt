package com.azhan.music.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.azhan.music.data.Song
import com.azhan.music.ui.theme.Neu
import com.azhan.music.ui.theme.NeuShapes
import com.azhan.music.ui.theme.neuInset

/**
 * 列表页的搜索框：凹陷槽 + 放大镜 + 一键清除。
 *
 * 用 `BasicTextField` 而不是 Material 的 `TextField` —— 后者是扁平风格，
 * 放进拟态界面会很违和（同 NeuSlider 的理由）。
 */
@Composable
fun NeuSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索文件名",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Neu.Background, NeuShapes.pill)
            .neuInset(NeuShapes.pill, stroke = 2.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Neu.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))

        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neu.TextSecondary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Neu.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清除",
                    tint = Neu.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 按文件名（显示名）过滤，忽略大小写与首尾空格。空查询返回原列表。 */
fun List<Song>.filterByQuery(query: String): List<Song> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return this
    return filter { it.title.contains(keyword, ignoreCase = true) }
}
