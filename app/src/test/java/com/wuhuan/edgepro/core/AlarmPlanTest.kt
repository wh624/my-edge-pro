package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmPlanTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(local: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(local), zone)
        .toInstant().toEpochMilli()

    @Test
    fun `multiple cron lines are all armed as separate upcoming times`() {
        val trigger = Trigger.Cron("0 0 8 * * *\n0 30 12 * * *")
        val times = AlarmPlan.upcomingMillis(trigger, millis("2026-10-14T09:00:00"), zone, limit = 4)
        assertEquals(
            listOf(
                millis("2026-10-14T12:30:00"),
                millis("2026-10-15T08:00:00"),
                millis("2026-10-15T12:30:00"),
                millis("2026-10-16T08:00:00"),
            ),
            times,
        )
    }

    @Test
    fun `catch up uses only the latest missed time`() {
        val eight = millis("2026-10-14T08:00:00")
        val noon = millis("2026-10-14T12:30:00")
        val evening = millis("2026-10-14T20:00:00")
        val now = millis("2026-10-14T12:40:00")
        assertEquals(noon, AlarmPlan.catchUpMarker(listOf(eight, noon, evening), now))
        assertNull(AlarmPlan.catchUpMarker(listOf(evening), now))
    }
}
