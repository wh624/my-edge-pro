package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CronExpressionTest {

    private fun next(expr: String, from: String) = CronExpression.parse(expr).next(LocalDateTime.parse(from))

    @Test
    fun `five fields defaults second to zero`() {
        assertEquals(LocalDateTime.parse("2026-10-14T06:50:00"), next("50 6 * * *", "2026-10-14T06:00:00"))
        assertEquals(LocalDateTime.parse("2026-10-15T06:50:00"), next("50 6 * * *", "2026-10-14T06:50:00"))
    }

    @Test
    fun `six fields with seconds`() {
        assertEquals(LocalDateTime.parse("2026-10-14T06:50:30"), next("30 50 6 * * *", "2026-10-14T06:50:00"))
    }

    @Test
    fun `step and range`() {
        assertEquals(LocalDateTime.parse("2026-10-14T10:15:00"), next("*/15 9-18 * * *", "2026-10-14T10:01:00"))
        assertEquals(LocalDateTime.parse("2026-10-15T09:00:00"), next("*/15 9-18 * * *", "2026-10-14T18:45:00"))
    }

    @Test
    fun `day of week names and sunday as 7`() {
        // 2026-10-14 是周三
        assertEquals(LocalDateTime.parse("2026-10-16T08:00:00"), next("0 8 * * FRI", "2026-10-14T00:00:00"))
        assertEquals(LocalDateTime.parse("2026-10-18T08:00:00"), next("0 8 * * 7", "2026-10-14T00:00:00"))
        assertEquals(LocalDateTime.parse("2026-10-18T08:00:00"), next("0 8 * * 0", "2026-10-14T00:00:00"))
        assertEquals(LocalDateTime.parse("2026-10-19T08:00:00"), next("0 8 * * MON-FRI", "2026-10-16T09:00:00"))
    }

    @Test
    fun `dom and dow both restricted means either`() {
        // 每月 1 号或周五
        assertEquals(LocalDateTime.parse("2026-10-16T00:00:00"), next("0 0 1 * 5", "2026-10-14T00:00:00"))
        assertEquals(LocalDateTime.parse("2026-11-01T00:00:00"), next("0 0 1 * 5", "2026-10-30T00:00:00"))
    }

    @Test
    fun `month boundaries and leap day`() {
        assertEquals(LocalDateTime.parse("2028-02-29T12:00:00"), next("0 12 29 2 *", "2026-10-14T00:00:00"))
        assertEquals(LocalDateTime.parse("2027-01-01T00:00:00"), next("0 0 1 JAN *", "2026-10-14T00:00:00"))
    }

    @Test
    fun `impossible date returns null`() {
        assertNull(next("0 0 31 2 *", "2026-10-14T00:00:00"))
    }

    @Test(expected = CronExpression.ParseException::class)
    fun `rejects wrong field count`() {
        CronExpression.parse("* * *")
    }

    @Test(expected = CronExpression.ParseException::class)
    fun `rejects out of range`() {
        CronExpression.parse("0 25 * * *")
    }

    @Test(expected = CronExpression.ParseException::class)
    fun `rejects zero step`() {
        CronExpression.parse("*/0 * * * *")
    }
}
