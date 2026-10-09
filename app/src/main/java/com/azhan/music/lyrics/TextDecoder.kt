package com.azhan.music.lyrics

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 歌词文件编码识别。
 * 中文歌词大量存在 GBK 编码，直接按 UTF-8 读会乱码，所以先严格试 UTF-8，失败再退 GBK。
 */
object TextDecoder {

    private val GBK: Charset? by lazy { runCatching { Charset.forName("GBK") }.getOrNull() }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Throwable) {
            val gbk = GBK
            if (gbk != null) runCatching { String(bytes, gbk) }.getOrNull() ?: String(bytes, Charsets.UTF_8)
            else String(bytes, Charsets.UTF_8)
        }
    }
}
