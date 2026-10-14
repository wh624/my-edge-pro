package com.wuhuan.edgepro.core

import java.time.ZoneId
import java.time.ZonedDateTime

object TriggerCalculator {

    /** 返回严格晚于 [from] 的下一次触发时间；规则非法或永不触发时返回 null。 */
    fun next(trigger: Trigger, from: ZonedDateTime): ZonedDateTime? = when (trigger) {
        is Trigger.Daily -> nextDaily(trigger, from)
        is Trigger.Cron -> CronExpression.parseOrNull(trigger.expression)
            ?.next(from.toLocalDateTime())
            ?.atZone(from.zone)
    }

    fun nextTimes(trigger: Trigger, from: ZonedDateTime, count: Int): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var cursor = from
        repeat(count) {
            val n = next(trigger, cursor) ?: return result
            result += n
            cursor = n
        }
        return result
    }

    fun nextMillis(trigger: Trigger, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        next(trigger, ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone))
            ?.toInstant()?.toEpochMilli()

    /** 校验触发器，返回错误信息；合法时返回 null。 */
    fun validate(trigger: Trigger): String? = when (trigger) {
        is Trigger.Daily -> runCatching { DailyTimes.parse(trigger.times) }.exceptionOrNull()?.message
        is Trigger.Cron -> runCatching { CronExpression.parse(trigger.expression) }.exceptionOrNull()?.message
    }

    fun describe(trigger: Trigger): String = when (trigger) {
        is Trigger.Daily -> {
            val days = if (trigger.weekdays.isEmpty() || trigger.weekdays.size == 7) "每天"
            else "每周" + trigger.weekdays.sorted().joinToString("、") { WEEKDAY_NAMES[it - 1] }
            "$days ${trigger.times}"
        }
        is Trigger.Cron -> "Cron ${trigger.expression}"
    }

    val WEEKDAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

    private fun nextDaily(trigger: Trigger.Daily, from: ZonedDateTime): ZonedDateTime? {
        val times = DailyTimes.parseOrNull(trigger.times) ?: return null
        val startDate = from.toLocalDate()
        for (offset in 0..7L) {
            val date = startDate.plusDays(offset)
            if (trigger.weekdays.isNotEmpty() && date.dayOfWeek.value !in trigger.weekdays) continue
            for (time in times) {
                val candidate = date.atTime(time).atZone(from.zone)
                if (candidate.isAfter(from)) return candidate
            }
        }
        return null
    }
}
