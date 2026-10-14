package com.wuhuan.edgepro.sched

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wuhuan.edgepro.App

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
        TaskRunnerService.startOrLog(context, taskId, scheduledAt = triggerAt, source = "定时")
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

/**
 * 不导出，只有 root 守护进程能发送。它的作用就是让系统拉起本进程：
 * App.onCreate 会补执行错过的任务并重新注册全部闹钟。
 */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
