package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class DailyTimesTest {

    @Test
    fun `parses HHmmss HHmm and colon formats with mixed separators`() {
        val times = DailyTimes.parse("065000，1230; 7:05  23:59:59,065000")
        assertEquals(
            listOf(LocalTime.of(6, 50), LocalTime.of(7, 5), LocalTime.of(12, 30), LocalTime.of(23, 59, 59)),
            times,
        )
    }

    @Test(expected = DailyTimes.ParseException::class)
    fun `rejects out of range`() {
        DailyTimes.parse("246000")
    }

    @Test(expected = DailyTimes.ParseException::class)
    fun `rejects wrong length`() {
        DailyTimes.parse("06500")
    }

    @Test
    fun `parseOrNull returns null for blank`() {
        assertNull(DailyTimes.parseOrNull("  "))
    }
}
