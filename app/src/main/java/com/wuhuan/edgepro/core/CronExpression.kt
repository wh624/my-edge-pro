package com.wuhuan.edgepro.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.BitSet

/**
 * Cron 表达式：5 段 `分 时 日 月 周`，或 6 段 `秒 分 时 日 月 周`。
 * 支持 `*` `?` `,` `-` `/`，月份/星期可用英文缩写；周 0 和 7 均为周日。
 * 日与周同时受限时，任一匹配即触发（与 Vixie cron 一致）。
 */
class CronExpression private constructor(
    val expression: String,
    private val seconds: BitSet,
    private val minutes: BitSet,
    private val hours: BitSet,
    private val daysOfMonth: BitSet,
    private val months: BitSet,
    private val daysOfWeek: BitSet,
    private val domRestricted: Boolean,
    private val dowRestricted: Boolean,
) {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun next(after: LocalDateTime): LocalDateTime? {
        var t = after.withNano(0).plusSeconds(1)
        val limit = after.plusYears(MAX_SEARCH_YEARS)
        while (!t.isAfter(limit)) {
            if (!months[t.monthValue]) {
                t = t.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay()
                continue
            }
            if (!dayMatches(t.toLocalDate())) {
                t = t.toLocalDate().plusDays(1).atStartOfDay()
                continue
            }
            if (!hours[t.hour]) {
                t = t.withMinute(0).withSecond(0).plusHours(1)
                continue
            }
            if (!minutes[t.minute]) {
                t = t.withSecond(0).plusMinutes(1)
                continue
            }
            if (!seconds[t.second]) {
                t = t.plusSeconds(1)
                continue
            }
            return t
        }
        return null
    }

    private fun dayMatches(date: LocalDate): Boolean {
        val dom = daysOfMonth[date.dayOfMonth]
        val dow = daysOfWeek[date.dayOfWeek.value % 7]
        return when {
            domRestricted && dowRestricted -> dom || dow
            domRestricted -> dom
            dowRestricted -> dow
            else -> true
        }
    }

    companion object {
        private const val MAX_SEARCH_YEARS = 5L

        private val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
            .mapIndexed { i, n -> n to i + 1 }.toMap()
        private val dowNames = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            .mapIndexed { i, n -> n to i }.toMap()

        fun parse(expression: String): CronExpression {
            val fields = expression.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val all = when (fields.size) {
                5 -> listOf("0") + fields
                6 -> fields
                else -> throw ParseException("Cron 需要 5 段（分 时 日 月 周）或 6 段（秒 分 时 日 月 周），当前 ${fields.size} 段")
            }
            val dowRaw = parseField(all[5], 0, 7, dowNames, "周")
            if (dowRaw[7]) {
                dowRaw.set(0)
                dowRaw.clear(7)
            }
            return CronExpression(
                expression = expression.trim(),
                seconds = parseField(all[0], 0, 59, emptyMap(), "秒"),
                minutes = parseField(all[1], 0, 59, emptyMap(), "分"),
                hours = parseField(all[2], 0, 23, emptyMap(), "时"),
                daysOfMonth = parseField(all[3], 1, 31, emptyMap(), "日"),
                months = parseField(all[4], 1, 12, monthNames, "月"),
                daysOfWeek = dowRaw,
                domRestricted = !isWildcard(all[3]),
                dowRestricted = !isWildcard(all[5]),
            )
        }

        fun parseOrNull(expression: String): CronExpression? = runCatching { parse(expression) }.getOrNull()

        private fun isWildcard(field: String) = field == "*" || field == "?"

        private fun parseField(field: String, min: Int, max: Int, names: Map<String, Int>, label: String): BitSet {
            val bits = BitSet(max + 1)
            for (part in field.split(',')) {
                if (part.isEmpty()) throw ParseException("$label 字段存在空项：$field")
                val (rangePart, stepPart) = part.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                val step = stepPart?.let {
                    it.toIntOrNull()?.takeIf { s -> s > 0 } ?: throw ParseException("$label 字段步长非法：$part")
                } ?: 1
                val (start, end) = when {
                    rangePart == "*" || rangePart == "?" -> min to max
                    rangePart.contains('-') -> {
                        val (a, b) = rangePart.split('-', limit = 2)
                        value(a, names, label, part) to value(b, names, label, part)
                    }
                    else -> {
                        val v = value(rangePart, names, label, part)
                        v to if (stepPart != null) max else v
                    }
                }
                if (start < min || end > max || start > end) {
                    throw ParseException("$label 字段超出范围 $min-$max：$part")
                }
                var v = start
                while (v <= end) {
                    bits.set(v)
                    v += step
                }
            }
            return bits
        }

        private fun value(text: String, names: Map<String, Int>, label: String, part: String): Int =
            text.toIntOrNull() ?: names[text.uppercase()] ?: throw ParseException("$label 字段无法识别：$part")
    }
}
