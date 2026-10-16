package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TriggerCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), zone)

    @Test
    fun `daily picks next time today then tomorrow`() {
        val t = Trigger.Daily("065000,123000")
        assertEquals(at("2026-10-14T12:30:00"), TriggerCalculator.next(t, at("2026-10-14T06:50:00")))
        assertEquals(at("2026-10-15T06:50:00"), TriggerCalculator.next(t, at("2026-10-14T12:30:00")))
    }

    @Test
    fun `daily respects weekdays`() {
        // 2026-10-14 周三，只在周一触发
        val t = Trigger.Daily("065000", setOf(1))
        assertEquals(at("2026-10-19T06:50:00"), TriggerCalculator.next(t, at("2026-10-14T00:00:00")))
    }

    @Test
    fun `invalid trigger yields null and validation message`() {
        val t = Trigger.Daily("abc")
        assertNull(TriggerCalculator.next(t, at("2026-10-14T00:00:00")))
        assertNotNull(TriggerCalculator.validate(t))
        assertNull(TriggerCalculator.validate(Trigger.Cron("0 8 * * *")))
    }

    @Test
    fun `multiple cron lines fire at the earliest next time`() {
        val t = Trigger.Cron("0 0 8 * * *\n0 30 12 * * *")
        assertEquals(at("2026-10-14T12:30:00"), TriggerCalculator.next(t, at("2026-10-14T09:00:00")))
        assertEquals(at("2026-10-15T08:00:00"), TriggerCalculator.next(t, at("2026-10-14T12:30:00")))
        assertNull(TriggerCalculator.validate(t))
        assertNotNull(TriggerCalculator.validate(Trigger.Cron("0 0 8 * * *\nnot cron")))
    }

    @Test
    fun `nextTimes returns consecutive fires`() {
        val list = TriggerCalculator.nextTimes(Trigger.Cron("0 0 8 * * *"), at("2026-10-14T09:00:00"), 3)
        assertEquals(listOf(at("2026-10-15T08:00:00"), at("2026-10-16T08:00:00"), at("2026-10-17T08:00:00")), list)
    }
}
