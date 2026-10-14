package com.wuhuan.edgepro.core

data class DaemonStatus(val running: Boolean, val pid: Int?, val bootHookInstalled: Boolean)

/**
 * Root 守护进程脚本。守护进程以 uid 0 运行，不会被针对应用 uid 的杀进程/强行停止波及；
 * 发现 App 进程不在时用 root 广播把它拉起，App 启动时会重新注册全部闹钟。
 */
object DaemonScripts {

    const val DIR = "/data/adb/edgepro"
    const val DAEMON = "$DIR/daemon.sh"
    const val PID_FILE = "$DIR/daemon.pid"
    const val BOOT_HOOK = "/data/adb/service.d/edgepro.sh"
    const val INTERVAL_SEC = 30
    private const val EOF = "__EDGEPRO_EOF__"

    /** MIUI/HyperOS 私有 AppOps：10008 自启动，10021 后台弹出界面。其他 ROM 上会失败并被忽略。 */
    fun permissionCommands(pkg: String): List<String> {
        require(ShellCommands.isValidPackage(pkg))
        return listOf(
            "appops set $pkg 10008 allow",
            "appops set $pkg 10021 allow",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "dumpsys deviceidle whitelist +$pkg",
        )
    }

    fun keepAliveAction(pkg: String) = "$pkg.action.KEEP_ALIVE"

    fun daemon(pkg: String): String {
        require(ShellCommands.isValidPackage(pkg))
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# my-edge-pro root 守护进程（由 App 自动生成，请勿手动修改）")
            appendLine("PKG=$pkg")
            appendLine("PIDF=$PID_FILE")
            appendLine("P=\$(cat \"\$PIDF\" 2>/dev/null)")
            appendLine("if [ -n \"\$P\" ] && [ \"\$P\" != \"\$\$\" ]; then")
            appendLine("  case \"\$(tr '\\0' ' ' < /proc/\$P/cmdline 2>/dev/null)\" in *edgepro/daemon.sh*) exit 0 ;; esac")
            appendLine("fi")
            appendLine("echo \$\$ > \"\$PIDF\"")
            appendLine("until [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 5; done")
            permissionCommands(pkg).forEach { appendLine("$it >/dev/null 2>&1") }
            appendLine("while true; do")
            appendLine("  # /data/data 在输入密码前不存在，不能用来判断卸载；/data/user_de 解锁前就在")
            appendLine("  if [ ! -d /data/user_de/0/\$PKG ]; then")
            appendLine("    if [ -d /data/user_de/0 ]; then rm -f $BOOT_HOOK \"\$PIDF\"; rm -rf $DIR; exit 0; fi")
            appendLine("  fi")
            appendLine("  if [ -z \"\$(pidof \$PKG)\" ]; then")
            appendLine("    am broadcast --user 0 -a ${keepAliveAction(pkg)} -n \$PKG/.sched.KeepAliveReceiver -f 0x10000020 >/dev/null 2>&1")
            appendLine("  fi")
            appendLine("  sleep $INTERVAL_SEC")
            append("done")
        }
    }

    fun bootHook(): String = """
        #!/system/bin/sh
        [ -f $DAEMON ] && nohup sh $DAEMON >/dev/null 2>&1 &
    """.trimIndent()

    /** 安装/更新脚本并保证守护进程运行；脚本内容变化时重启守护进程。最后输出状态。 */
    fun install(pkg: String): String = buildString {
        appendLine("mkdir -p $DIR /data/adb/service.d")
        appendLine("cat > $DAEMON.new <<'$EOF'")
        appendLine(daemon(pkg))
        appendLine(EOF)
        appendLine("cat > $BOOT_HOOK <<'$EOF'")
        appendLine(bootHook())
        appendLine(EOF)
        appendLine("chmod 755 $BOOT_HOOK")
        appendLine("if cmp -s $DAEMON.new $DAEMON; then")
        appendLine("  rm -f $DAEMON.new")
        appendLine("else")
        appendLine("  mv -f $DAEMON.new $DAEMON; chmod 755 $DAEMON")
        appendLine("  P=\$(cat $PID_FILE 2>/dev/null); [ -n \"\$P\" ] && kill \$P 2>/dev/null; rm -f $PID_FILE")
        appendLine("fi")
        appendLine(startCommand())
        appendLine("sleep 1")
        append(status())
    }

    fun uninstall(): String = """
        P=${'$'}(cat $PID_FILE 2>/dev/null); [ -n "${'$'}P" ] && kill ${'$'}P 2>/dev/null
        rm -f $BOOT_HOOK; rm -rf $DIR
        echo daemon=stopped
        echo boot=missing
    """.trimIndent()

    fun status(): String = """
        P=${'$'}(cat $PID_FILE 2>/dev/null)
        if [ -n "${'$'}P" ] && tr '\0' ' ' < /proc/${'$'}P/cmdline 2>/dev/null | grep -q edgepro/daemon.sh; then echo "daemon=running:${'$'}P"; else echo daemon=stopped; fi
        if [ -f $BOOT_HOOK ]; then echo boot=installed; else echo boot=missing; fi
    """.trimIndent()

    fun diagnose(pkg: String): String {
        require(ShellCommands.isValidPackage(pkg))
        return """
            echo "== 守护进程 =="; ${status().replace("\n", "; ")}
            echo "== App 进程 =="; pidof $pkg || echo 未运行
            echo "== MIUI 自启动(10008) =="; appops get $pkg 10008 2>&1 | head -n 3
            echo "== 电池优化白名单 =="; dumpsys deviceidle whitelist | grep $pkg || echo 不在白名单
            echo "== 待机分组 =="; am get-standby-bucket $pkg 2>&1
            echo "== 后台运行 =="; cmd appops get $pkg RUN_ANY_IN_BACKGROUND 2>&1
            echo "== 用户是否已解锁 =="; if [ -d /data/system_ce/0 ]; then echo 已解锁; else echo 未解锁（重启后还没输入密码）; fi
            echo "== 设备加密数据目录 =="; ls -d /data/user_de/0/$pkg 2>&1 || echo 不存在
            echo "== 本应用已注册的闹钟 =="; dumpsys alarm | grep -c "$pkg broadcastIntent"
            dumpsys alarm | grep -E "$pkg" | head -n 12
        """.trimIndent()
    }

    private fun startCommand(): String =
        "if command -v setsid >/dev/null 2>&1; then setsid nohup sh $DAEMON >/dev/null 2>&1 </dev/null & " +
            "else nohup sh $DAEMON >/dev/null 2>&1 </dev/null & fi"

    fun parseStatus(output: String): DaemonStatus {
        val line = output.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("daemon=") }.orEmpty()
        val running = line.startsWith("daemon=running")
        return DaemonStatus(
            running = running,
            pid = if (running) line.substringAfter(':', "").toIntOrNull() else null,
            bootHookInstalled = output.lineSequence().any { it.trim() == "boot=installed" },
        )
    }
}
