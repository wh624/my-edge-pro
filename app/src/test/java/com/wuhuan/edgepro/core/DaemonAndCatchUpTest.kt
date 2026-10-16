package com.wuhuan.edgepro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonAndCatchUpTest {

    private val pkg = "com.wuhuan.edgepro"

    @Test
    fun `daemon script wakes app via explicit root broadcast including stopped packages`() {
        val s = DaemonScripts.daemon(pkg)
        assertTrue(s.contains("pidof \$PKG"))
        assertTrue(s.contains("-n \$PKG/.sched.KeepAliveReceiver -f 0x10000020"))
        assertTrue(s.contains("/data/user_de/0/\$PKG"))
        assertFalse(s.contains("[ ! -d /data/data/"))
        assertTrue(s.contains("appops set $pkg 10008 allow"))
        assertTrue(s.contains("appops set $pkg 10017 allow"))
        assertTrue(s.contains("sys.boot_completed"))
    }

    @Test
    fun `install script embeds daemon and boot hook with heredoc and ends with status`() {
        val s = DaemonScripts.install(pkg)
        assertEquals(2, Regex("<<'__EDGEPRO_EOF__'").findAll(s).count())
        assertFalse(DaemonScripts.daemon(pkg).contains("__EDGEPRO_EOF__"))
        assertTrue(s.contains(DaemonScripts.BOOT_HOOK))
        assertTrue(s.trimEnd().endsWith("echo boot=missing; fi"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid package`() {
        DaemonScripts.daemon("x;reboot")
    }

    @Test
    fun `parses status output`() {
        val running = DaemonScripts.parseStatus("daemon=running:1234\nboot=installed\n")
        assertTrue(running.running)
        assertEquals(1234, running.pid)
        assertTrue(running.bootHookInstalled)
        val stopped = DaemonScripts.parseStatus("daemon=stopped\nboot=missing")
        assertFalse(stopped.running)
        assertNull(stopped.pid)
        assertFalse(stopped.bootHookInstalled)
    }

    private val step = listOf(Step.Delay(1))

    @Test
    fun `catch up only missed runs inside grace window`() {
        val now = 1_000_000L
        val grace = 30 * 60_000L
        val missed = Task(id = "a", steps = step, lastRunAt = now - 86_400_000L)
        val ran = Task(id = "b", steps = step, lastRunAt = now - 1_000L)
        val tooOld = Task(id = "c", steps = step)
        val future = Task(id = "d", steps = step)
        val disabled = Task(id = "e", steps = step, enabled = false)
        val expected = mapOf(
            "a" to now - 60_000L,
            "b" to now - 60_000L,
            "c" to now - grace - 1,
            "d" to now + 60_000L,
            "e" to now - 60_000L,
        )
        val due = CatchUp.due(listOf(missed, ran, tooOld, future, disabled), expected, now, grace)
        assertEquals(listOf("a" to now - 60_000L), due.map { it.first.id to it.second })
        assertTrue(CatchUp.due(listOf(missed), expected, now, 0).isEmpty())
    }

    @Test
    fun `launch tasks wait for the first unlock`() {
        val launch = listOf(Step.Launch(listOf(AppTarget("com.a.b"))))
        val shell = listOf(Step.Shell("echo hi"))
        assertTrue(DirectBoot.deferLaunch(launch, userUnlocked = false))
        assertFalse(DirectBoot.deferLaunch(launch, userUnlocked = true))
        assertFalse(DirectBoot.deferLaunch(shell, userUnlocked = false))
    }

    @Test
    fun `duplicate delivery is detected by last run time`() {
        assertTrue(CatchUp.alreadyRan(Task(lastRunAt = 2_000L), 1_500L))
        assertFalse(CatchUp.alreadyRan(Task(lastRunAt = 1_000L), 1_500L))
        assertFalse(CatchUp.alreadyRan(Task(), 1_500L))
    }
}
