package com.wuhuan.edgepro.core

import java.time.LocalTime

/**
 * 解析每日定时时间列表。支持 `HHmmss`、`HHmm`、`H:mm`、`HH:mm:ss`，
 * 多个时间用 `,` `，` `;` `；` 或空白分隔。
 */
object DailyTimes {

    class ParseException(message: String) : IllegalArgumentException(message)

    private val separators = Regex("[,，;；\\s]+")

    fun parse(text: String): List<LocalTime> {
        val tokens = text.trim().split(separators).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) throw ParseException("请输入至少一个时间，如 065000")
        return tokens.map(::parseToken).distinct().sorted()
    }

    fun parseOrNull(text: String): List<LocalTime>? = runCatching { parse(text) }.getOrNull()

    /** 按用户输入的顺序拆开，不校验、不去重，供编辑界面逐条展示。 */
    fun rawEntries(text: String): List<String> =
        text.trim().split(separators).filter { it.isNotEmpty() }

    private fun parseToken(token: String): LocalTime {
        val parts: List<String> = if (token.contains(':') || token.contains('：')) {
            token.split(':', '：')
        } else {
            if (!token.all(Char::isDigit)) throw ParseException("无法识别的时间：$token")
            when (token.length) {
                6 -> listOf(token.substring(0, 2), token.substring(2, 4), token.substring(4, 6))
                4 -> listOf(token.substring(0, 2), token.substring(2, 4))
                else -> throw ParseException("时间格式应为 HHmmss 或 HHmm：$token")
            }
        }
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
            throw ParseException("无法识别的时间：$token")
        }
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val s = parts.getOrNull(2)?.toInt() ?: 0
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) throw ParseException("时间超出范围：$token")
        return LocalTime.of(h, m, s)
    }
}
