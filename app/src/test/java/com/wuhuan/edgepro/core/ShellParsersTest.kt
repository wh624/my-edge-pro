package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellParsersTest {

    @Test
    fun `parses pm list users`() {
        val out = """
            Users:
            	UserInfo{0:机主:c13} running
            	UserInfo{999:XSpace:801010} running
            	UserInfo{10:Work: profile:1030}
        """.trimIndent()
        val users = ShellParsers.parseUsers(out)
        assertEquals(listOf(0, 10, 999), users.map { it.id })
        assertEquals("机主", users[0].name)
        assertEquals("Work: profile", users[1].name)
        assertFalse(users[1].running)
        assertTrue(users[2].running)
    }

    @Test
    fun `parses package list`() {
        val out = "package:com.tencent.mm\npackage:com.eg.android.AlipayGphone uid:10123\n\nfoo"
        assertEquals(setOf("com.tencent.mm", "com.eg.android.AlipayGphone"), ShellParsers.parsePackages(out))
    }

    @Test
    fun `parses running app processes by user`() {
        val out = """
            USER           NAME
            root           init
            system         system_server
            u0_a123        com.tencent.mm
            u0_a123        com.tencent.mm:push
            u999_a123      com.tencent.mm
            u0_i5          com.android.chrome:sandboxed_process0
            u0_a200        /system/bin/sh
        """.trimIndent()
        assertEquals(
            setOf(RunningApp("com.tencent.mm", 0), RunningApp("com.tencent.mm", 999)),
            ShellParsers.parseRunningApps(out),
        )
    }

    @Test
    fun `parses resolved component`() {
        val out = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\ncom.tencent.mm/.ui.LauncherUI\n"
        assertEquals("com.tencent.mm/.ui.LauncherUI", ShellParsers.parseResolvedComponent(out))
        assertNull(ShellParsers.parseResolvedComponent("No activity found"))
    }

    @Test
    fun `splits sections`() {
        val out = "@@SECTION:a@@\nline1\nline2\n@@SECTION:b@@\nx\n"
        val s = ShellParsers.splitSections(out)
        assertEquals("line1\nline2\n", s["a"])
        assertEquals("x\n\n", s["b"])
    }
}
