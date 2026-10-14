package com.wuhuan.edgepro.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AppTarget(
    val packageName: String,
    val userId: Int = 0,
    val label: String = packageName,
)

@Serializable
enum class StopMode { FORCE_STOP, KILL }

@Serializable
sealed interface Trigger {
    /** [times] 保留用户输入的原始文本（如 "065000,123000"），[weekdays] 为 ISO 星期 1..7，空表示每天。 */
    @Serializable
    @SerialName("daily")
    data class Daily(val times: String, val weekdays: Set<Int> = emptySet()) : Trigger

    @Serializable
    @SerialName("cron")
    data class Cron(val expression: String) : Trigger
}

@Serializable
sealed interface Step {
    @Serializable
    @SerialName("launch")
    data class Launch(val apps: List<AppTarget> = emptyList(), val intervalMs: Long = 1500) : Step

    @Serializable
    @SerialName("stop")
    data class Stop(val apps: List<AppTarget> = emptyList(), val mode: StopMode = StopMode.FORCE_STOP) : Step

    @Serializable
    @SerialName("clean")
    data class CleanBackground(
        val whitelist: List<AppTarget> = emptyList(),
        val mode: StopMode = StopMode.FORCE_STOP,
        val includeSystem: Boolean = false,
        val allUsers: Boolean = true,
    ) : Step

    @Serializable
    @SerialName("shell")
    data class Shell(val script: String = "", val timeoutSec: Int = 60) : Step

    @Serializable
    @SerialName("delay")
    data class Delay(val seconds: Int = 5) : Step
}

@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger.Daily("065000"),
    val steps: List<Step> = emptyList(),
    val lastRunAt: Long? = null,
    val lastSuccess: Boolean? = null,
)

@Serializable
data class LogEntry(
    val time: Long,
    val taskId: String,
    val taskName: String,
    val success: Boolean,
    val summary: String,
    val detail: String,
)

fun Step.title(): String = when (this) {
    is Step.Launch -> "启动应用"
    is Step.Stop -> "停止应用"
    is Step.CleanBackground -> "清理后台"
    is Step.Shell -> "Shell 命令"
    is Step.Delay -> "延时"
}

fun Step.summary(): String = when (this) {
    is Step.Launch -> "启动 ${apps.size} 个应用"
    is Step.Stop -> "停止 ${apps.size} 个应用"
    is Step.CleanBackground -> if (whitelist.isEmpty()) "清理后台" else "清理后台（白名单 ${whitelist.size}）"
    is Step.Shell -> "Shell: " + script.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(30)
    is Step.Delay -> "延时 ${seconds}s"
}

fun userLabel(userId: Int): String = when (userId) {
    0 -> "主空间"
    999 -> "双开(999)"
    else -> "用户 $userId"
}
