package com.wuhuan.edgepro.sched

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.core.LogEntry

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
        try {
            TaskRunnerService.start(context, taskId, scheduledAt = triggerAt)
        } catch (e: IllegalStateException) {
            // Android 12+ 后台启动前台服务被拒（通常是非精确闹钟触发）
            val app = context.applicationContext as App
            val task = app.tasks.get(taskId) ?: return
            app.scheduler.schedule(task, afterMillis = maxOf(System.currentTimeMillis(), triggerAt))
            app.logs.add(
                LogEntry(
                    time = System.currentTimeMillis(),
                    taskId = task.id,
                    taskName = task.name.ifBlank { "未命名任务" },
                    success = false,
                    summary = "[定时] 系统拒绝后台启动执行服务",
                    detail = "${e.message}\n请在设置中执行「一键保活优化」并开启「闹钟模式」。",
                )
            )
        }
    }

    companion object {
        const val ACTION_RUN = "com.wuhuan.edgepro.action.RUN_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}

/** 开机、时间/时区变更、应用更新后重排全部闹钟（App.onCreate 中已执行）。 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as App
        app.scheduler.scheduleAll(app.tasks.items.value)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
