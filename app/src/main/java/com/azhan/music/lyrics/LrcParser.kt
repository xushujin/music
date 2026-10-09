package com.azhan.music.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val offsetMs: Long = 0L,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** 二分找「当前应该高亮哪一行」，返回 -1 表示还没到第一行。 */
    fun indexAt(timeMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= timeMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    companion object {
        val EMPTY = Lyrics(emptyList())
    }
}

/**
 * LRC 解析。
 * 支持：[mm:ss]、[mm:ss.x]、[mm:ss.xx]、[mm:ss.xxx]、[mm:ss:xx]，
 * 一行多个时间戳，以及 [ti:] [ar:] [al:] [offset:] 标签。
 * 同一时间戳出现两次时，第二行当作翻译合并到第一行（中文双语歌词的常见写法）。
 */
object LrcParser {

    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val ANY_TAG = Regex("\\[[^\\]]*]")
    private val META_TAG = Regex("\\[(ti|ar|al|by|offset)\\s*:\\s*([^\\]]*)]", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Lyrics {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L
        val entries = ArrayList<Pair<Long, String>>()

        for (rawLine in raw.split('\n')) {
            val line = rawLine.trim('\r', ' ', '\t', '\uFEFF')
            if (line.isEmpty()) continue

            for (meta in META_TAG.findAll(line)) {
                val value = meta.groupValues[2].trim()
                when (meta.groupValues[1].lowercase()) {
                    "ti" -> title = value
                    "ar" -> artist = value
                    "al" -> album = value
                    "offset" -> offset = value.toLongOrNull() ?: 0L
                }
            }

            val stamps = TIME_TAG.findAll(line).toList()
            if (stamps.isEmpty()) continue

            val text = line.replace(ANY_TAG, "").trim()
            if (text.isEmpty()) continue

            for (stamp in stamps) {
                val minute = stamp.groupValues[1].toLongOrNull() ?: continue
                val second = stamp.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = stamp.groupValues[3]
                val fraction = when {
                    fractionRaw.isEmpty() -> 0L
                    fractionRaw.length == 1 -> fractionRaw.toLong() * 100
                    fractionRaw.length == 2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.substring(0, 3).toLong()
                }
                entries.add((minute * 60_000 + second * 1000 + fraction + offset) to text)
            }
        }

        if (entries.isEmpty()) return Lyrics(emptyList(), title, artist, album, offset)

        entries.sortBy { it.first }

        val merged = ArrayList<LyricLine>(entries.size)
        for ((time, text) in entries) {
            val last = merged.lastOrNull()
            if (last != null && last.timeMs == time) {
                val translation = if (last.translation == null) text else "${last.translation} / $text"
                merged[merged.size - 1] = last.copy(translation = translation)
            } else {
                merged.add(LyricLine(timeMs = time.coerceAtLeast(0L), text = text))
            }
        }

        return Lyrics(merged, title, artist, album, offset)
    }
}
