package com.wuhuan.edgepro.core

data class UserSpace(val id: Int, val name: String, val running: Boolean)

data class RunningApp(val packageName: String, val userId: Int)

object ShellParsers {

    private val userInfoRegex = Regex("""UserInfo\{(\d+):(.*):([0-9a-fA-F]+)\}(.*)""")
    private val appUidRegex = Regex("""^u(\d+)_a\d+$""")
    private val componentRegex = Regex("""^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$""")
    private val sectionRegex = Regex("""^@@SECTION:([A-Za-z0-9_]+)@@$""")

    /** 解析 `pm list users`。 */
    fun parseUsers(output: String): List<UserSpace> = output.lineSequence()
        .mapNotNull { line -> userInfoRegex.find(line.trim()) }
        .map { m ->
            UserSpace(
                id = m.groupValues[1].toInt(),
                name = m.groupValues[2],
                running = m.groupValues[4].contains("running"),
            )
        }
        .distinctBy { it.id }
        .sortedBy { it.id }
        .toList()

    /** 解析 `pm list packages`。 */
    fun parsePackages(output: String): Set<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    /** 解析 `ps -A -o USER,NAME`，只保留普通应用进程（uN_aXXX）。 */
    fun parseRunningApps(output: String): Set<RunningApp> = output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val userId = appUidRegex.find(parts.first())?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
            val pkg = parts.last().substringBefore(':')
            if (!ShellCommands.isValidPackage(pkg)) return@mapNotNull null
            RunningApp(pkg, userId)
        }
        .toSet()

    /** 解析 `cmd package resolve-activity --brief` 的输出，返回 `pkg/cls` 组件名。 */
    fun parseResolvedComponent(output: String): String? = output.lineSequence()
        .map { it.trim() }
        .lastOrNull { componentRegex.matches(it) }

    /** `settings get secure default_input_method` → 包名。 */
    fun parseComponentPackage(output: String): String? = output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.contains('/') }
        ?.substringBefore('/')
        ?.takeIf { ShellCommands.isValidPackage(it) }

    /** 按 `@@SECTION:name@@` 标记切分输出。 */
    fun splitSections(output: String): Map<String, String> {
        val result = linkedMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in output.lineSequence()) {
            val m = sectionRegex.find(line.trim())
            if (m != null) {
                current = StringBuilder().also { result[m.groupValues[1]] = it }
            } else {
                current?.appendLine(line)
            }
        }
        return result.mapValues { it.value.toString() }
    }
}
