package com.wuhuan.edgepro.core

/** 生成 root 脚本。所有拼接进脚本的包名都必须先通过 [isValidPackage]。 */
object ShellCommands {

    private val packageRegex = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    /** 清理后台时永远不会被停止的系统关键进程。 */
    val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings",
        "com.android.providers.settings",
        "com.android.shell",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.se",
        "com.android.networkstack",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "me.bmax.apatch",
        "org.lsposed.manager",
        "com.miui.home",
        "com.miui.securitycenter",
        "com.android.inputmethod.latin",
    )

    fun isValidPackage(pkg: String): Boolean = packageRegex.matches(pkg)

    private fun requireValid(target: AppTarget) {
        require(isValidPackage(target.packageName)) { "非法包名：${target.packageName}" }
        require(target.userId >= 0) { "非法用户 ID：${target.userId}" }
    }

    /**
     * 以桌面点击的方式启动应用：先解析 LAUNCHER Activity，再 `am start --user N`。
     * 主空间解析失败时退回 monkey。
     */
    fun launch(target: AppTarget): String {
        requireValid(target)
        val pkg = target.packageName
        val u = target.userId
        val plain = "am start --user $u -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n \"\$comp\""
        val dismiss = "am start --dismiss-keyguard-if-insecure --user $u -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n \"\$comp\""
        return buildString {
            if (u != 0) appendLine("am start-user $u >/dev/null 2>&1")
            appendLine("comp=\$(cmd package resolve-activity --brief --user $u -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg 2>/dev/null | tail -n 1)")
            appendLine("case \"\$comp\" in")
            appendLine("  */*)")
            appendLine("    input keyevent KEYCODE_WAKEUP >/dev/null 2>&1")
            appendLine("    out=\$($dismiss 2>&1)")
            appendLine("    case \"\$out\" in *Unknown*option*|*unknown*option*) out=\$($plain 2>&1) ;; esac")
            appendLine("    printf '%s\\n' \"\$out\"")
            appendLine("    case \"\$out\" in *Error*|*Exception*)")
            appendLine("      if [ \"\$(getprop ro.crypto.type)\" = file ] && [ ! -d /data/system_ce/$u ]; then")
            appendLine("        echo USER_LOCKED")
            appendLine("        exit 3")
            appendLine("      fi")
            appendLine("      exit 2 ;;")
            appendLine("    esac ;;")
            if (u == 0) {
                appendLine("  *) monkey -p $pkg -c android.intent.category.LAUNCHER 1 ;;")
            } else {
                appendLine("  *) echo \"未找到 $pkg 在用户 $u 中的启动入口\" >&2; exit 2 ;;")
            }
            append("esac")
        }
    }

    fun stop(target: AppTarget, mode: StopMode): String {
        requireValid(target)
        return when (mode) {
            StopMode.FORCE_STOP -> "am force-stop --user ${target.userId} ${target.packageName}"
            StopMode.KILL -> "am kill --user ${target.userId} ${target.packageName}"
        }
    }

    fun stopAll(targets: Collection<AppTarget>, mode: StopMode): String =
        targets.joinToString("\n") { stop(it, mode) + " && echo \"已停止 ${it.packageName} (user ${it.userId})\"" }

    /** 收集清理后台所需信息，输出用 [ShellParsers.splitSections] 切分。 */
    fun cleanProbe(userIds: Collection<Int>, includeSystem: Boolean): String = buildString {
        appendLine("echo '@@SECTION:ps@@'")
        appendLine("ps -A -o USER,NAME 2>/dev/null || ps -A")
        for (u in userIds) {
            require(u >= 0)
            appendLine("echo '@@SECTION:pkgs_$u@@'")
            appendLine(if (includeSystem) "pm list packages --user $u" else "pm list packages -3 --user $u")
        }
        appendLine("echo '@@SECTION:home@@'")
        appendLine("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -n 1")
        appendLine("echo '@@SECTION:ime@@'")
        append("settings get secure default_input_method")
    }

    /** 计算清理后台要停止的目标。 */
    fun cleanTargets(
        probeOutput: String,
        userIds: Collection<Int>,
        whitelist: Collection<AppTarget>,
        selfPackage: String,
    ): List<AppTarget> {
        val sections = ShellParsers.splitSections(probeOutput)
        val running = ShellParsers.parseRunningApps(sections["ps"].orEmpty())
        val excluded = buildSet {
            addAll(PROTECTED_PACKAGES)
            add(selfPackage)
            ShellParsers.parseComponentPackage(sections["home"].orEmpty())?.let(::add)
            ShellParsers.parseComponentPackage(sections["ime"].orEmpty())?.let(::add)
        }
        val whitelistKeys = whitelist.map { it.packageName to it.userId }.toSet()
        return userIds.flatMap { u ->
            val candidates = ShellParsers.parsePackages(sections["pkgs_$u"].orEmpty())
            running.asSequence()
                .filter { it.userId == u && it.packageName in candidates }
                .filter { it.packageName !in excluded }
                .filter { (it.packageName to u) !in whitelistKeys }
                .map { AppTarget(it.packageName, u) }
                .sortedBy { it.packageName }
                .toList()
        }
    }

    /** 本应用的保活与权限优化。 */
    fun optimizeSelf(pkg: String): String {
        require(isValidPackage(pkg))
        return (DaemonScripts.permissionCommands(pkg) + listOf(
            "cmd appops set $pkg SCHEDULE_EXACT_ALARM allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            "am set-standby-bucket $pkg active",
        )).joinToString("\n") { "$it >/dev/null 2>&1 && echo \"OK: $it\" || echo \"跳过: $it\"" }
    }
}
