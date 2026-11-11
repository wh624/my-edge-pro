package com.wuhuan.edgepro.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 同一个任务接下来要挂上的闹钟。
 * 小米会把「同一个 PendingIntent 再注册一次」丢掉，多条 Cron 如果共用一条闹钟，
 * 第一次响过之后，后面的时间点就再也挂不上。所以按每一次触发时间拆开。
 */
object AlarmPlan {

    /** 预先挂上的次数。一次响完会补上下一批，中间被杀掉也不会把后面的 Cron 弄丢。 */
    const val MAX_ARMED = 8

    fun upcomingMillis(
        trigger: Trigger,
        afterMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = MAX_ARMED,
    ): List<Long> {
        if (limit <= 0) return emptyList()
        val from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
        return TriggerCalculator.nextTimes(trigger, from, limit).map { it.toInstant().toEpochMilli() }
    }

    /** 补执行只认已经错过的最近一次；更早的同一次任务不再补。 */
    fun catchUpMarker(armed: Collection<Long>, now: Long): Long? =
        armed.filter { it <= now }.maxOrNull()
}
