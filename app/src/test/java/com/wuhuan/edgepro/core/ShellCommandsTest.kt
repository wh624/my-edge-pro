package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCommandsTest {

    @Test
    fun `validates package names`() {
        assertTrue(ShellCommands.isValidPackage("com.tencent.mm"))
        assertFalse(ShellCommands.isValidPackage("com.x; rm -rf /"))
        assertFalse(ShellCommands.isValidPackage("nodot"))
        assertFalse(ShellCommands.isValidPackage("\$(id).a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `launch rejects injection`() {
        ShellCommands.launch(AppTarget("com.a;reboot"))
    }

    @Test
    fun `launch for clone user uses user flag and no monkey fallback`() {
        val s = ShellCommands.launch(AppTarget("com.tencent.mm", 999))
        assertTrue(s.contains("am start-user 999"))
        assertTrue(s.contains("--user 999"))
        assertFalse(s.contains("KEYCODE_WAKEUP"))
        assertFalse(s.contains("--dismiss-keyguard-if-insecure"))
        assertTrue(s.contains("mWakefulness="))
        assertTrue(s.contains("KEYCODE_SLEEP"))
        assertTrue(s.contains("USER_LOCKED"))
        assertTrue(s.contains("/data/system_ce/999"))
        assertFalse(s.contains("monkey"))
        assertTrue(ShellCommands.launch(AppTarget("com.tencent.mm", 0)).contains("monkey -p com.tencent.mm"))
    }

    @Test
    fun `stop modes`() {
        assertEquals("am force-stop --user 0 com.a.b", ShellCommands.stop(AppTarget("com.a.b"), StopMode.FORCE_STOP))
        assertEquals("am kill --user 999 com.a.b", ShellCommands.stop(AppTarget("com.a.b", 999), StopMode.KILL))
    }

    @Test
    fun `clean targets exclude whitelist self home ime and non candidates`() {
        val probe = """
            @@SECTION:ps@@
            USER NAME
            u0_a1 com.tencent.mm
            u0_a2 com.eg.android.AlipayGphone
            u0_a3 com.my.launcher
            u0_a4 com.sohu.inputmethod.sogou
            u0_a5 com.wuhuan.edgepro
            u0_a6 com.android.systemui
            u0_a7 com.whitelisted.app
            u999_a1 com.tencent.mm
            @@SECTION:pkgs_0@@
            package:com.tencent.mm
            package:com.eg.android.AlipayGphone
            package:com.my.launcher
            package:com.sohu.inputmethod.sogou
            package:com.wuhuan.edgepro
            package:com.whitelisted.app
            @@SECTION:pkgs_999@@
            package:com.tencent.mm
            @@SECTION:home@@
            com.my.launcher/.Launcher
            @@SECTION:ime@@
            com.sohu.inputmethod.sogou/.SogouIME
        """.trimIndent()
        val targets = ShellCommands.cleanTargets(
            probe,
            userIds = listOf(0, 999),
            whitelist = listOf(AppTarget("com.whitelisted.app", 0)),
            selfPackage = "com.wuhuan.edgepro",
        )
        assertEquals(
            listOf(
                AppTarget("com.eg.android.AlipayGphone", 0),
                AppTarget("com.tencent.mm", 0),
                AppTarget("com.tencent.mm", 999),
            ),
            targets,
        )
    }
}
