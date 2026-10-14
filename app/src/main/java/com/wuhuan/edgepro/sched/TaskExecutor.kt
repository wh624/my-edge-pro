package com.wuhuan.edgepro.sched

import android.content.Context
import com.wuhuan.edgepro.core.AppTarget
import com.wuhuan.edgepro.core.ShellCommands
import com.wuhuan.edgepro.core.ShellParsers
import com.wuhuan.edgepro.core.Step
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.core.title
import com.wuhuan.edgepro.root.RootShell
import kotlinx.coroutines.delay

data class StepReport(val title: String, val success: Boolean, val output: String)

data class ExecutionReport(val steps: List<StepReport>) {
    val success: Boolean get() = steps.all { it.success }
    val summary: String
        get() = if (success) "全部 ${steps.size} 步成功"
        else "失败：" + steps.filterNot { it.success }.joinToString("、") { it.title }

    fun detail(): String = steps.mapIndexed { i, s ->
        "[${i + 1}] ${s.title} ${if (s.success) "✓" else "✗"}\n${s.output.trim()}"
    }.joinToString("\n\n")
}

/** 顺序执行任务的所有步骤；单步失败不会中断后续步骤。 */
class TaskExecutor(private val context: Context) {

    suspend fun run(task: Task): ExecutionReport {
        if (!RootShell.checkRoot()) {
            return ExecutionReport(listOf(StepReport("Root 检查", false, "未获取到 Root 权限，请在 Magisk/KernelSU 中授权本应用")))
        }
        return ExecutionReport(task.steps.map { step ->
            runCatching { runStep(step) }.getOrElse { e ->
                StepReport(step.title(), false, "异常：${e.message}")
            }
        })
    }

    private suspend fun runStep(step: Step): StepReport = when (step) {
        is Step.Launch -> launch(step)
        is Step.Stop -> {
            if (step.apps.isEmpty()) StepReport(step.title(), true, "未选择应用")
            else exec(step.title(), ShellCommands.stopAll(step.apps, step.mode))
        }
        is Step.CleanBackground -> clean(step)
        is Step.Shell -> exec(step.title(), step.script, step.timeoutSec.toLong())
        is Step.Delay -> {
            delay(step.seconds * 1000L)
            StepReport(step.title(), true, "等待 ${step.seconds}s")
        }
    }

    private suspend fun launch(step: Step.Launch): StepReport {
        if (step.apps.isEmpty()) return StepReport(step.title(), true, "未选择应用")
        val lines = mutableListOf<String>()
        var allOk = true
        step.apps.forEachIndexed { i, app ->
            if (i > 0 && step.intervalMs > 0) delay(step.intervalMs)
            val r = RootShell.exec(ShellCommands.launch(app), timeoutSec = 30)
            val ok = r.ok && !r.out.contains("Error") && !r.err.contains("Error")
            allOk = allOk && ok
            lines += "${if (ok) "✓" else "✗"} ${app.display()}" + if (ok) "" else "\n  ${r.combined.replace("\n", "\n  ")}"
        }
        return StepReport(step.title(), allOk, lines.joinToString("\n"))
    }

    private suspend fun clean(step: Step.CleanBackground): StepReport {
        val userIds = if (step.allUsers) {
            val r = RootShell.exec("pm list users", timeoutSec = 30)
            ShellParsers.parseUsers(r.out).filter { it.running }.map { it.id }.ifEmpty { listOf(0) }
        } else {
            listOf(0)
        }
        val probe = RootShell.exec(ShellCommands.cleanProbe(userIds, step.includeSystem), timeoutSec = 60)
        if (!probe.ok && probe.out.isBlank()) return StepReport(step.title(), false, probe.combined)
        val targets = ShellCommands.cleanTargets(probe.out, userIds, step.whitelist, context.packageName)
        if (targets.isEmpty()) return StepReport(step.title(), true, "没有需要清理的后台应用")
        val r = RootShell.exec(ShellCommands.stopAll(targets, step.mode), timeoutSec = 30L + targets.size * 5L)
        return StepReport(step.title(), r.ok, "共 ${targets.size} 个\n" + r.combined)
    }

    private suspend fun exec(title: String, script: String, timeoutSec: Long = 60): StepReport {
        val r = RootShell.exec(script, timeoutSec)
        val output = buildString {
            append(r.combined.ifBlank { "(无输出)" })
            if (!r.ok) append("\n退出码：${r.code}")
        }
        return StepReport(title, r.ok, output)
    }

    private fun AppTarget.display() = if (userId == 0) "$label ($packageName)" else "$label ($packageName, user $userId)"
}
