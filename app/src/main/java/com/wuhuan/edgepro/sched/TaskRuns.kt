package com.wuhuan.edgepro.sched

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import android.os.UserManager
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.R
import com.wuhuan.edgepro.core.CatchUp
import com.wuhuan.edgepro.core.DirectBoot
import com.wuhuan.edgepro.core.LogEntry
import com.wuhuan.edgepro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** 真正执行任务的地方。前台服务和「系统不允许启动前台服务」时的进程内回退都走这里。 */
object TaskRuns {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val resultIds = AtomicInteger(2000)

    private val _running = MutableStateFlow<String?>(null)
    val running: StateFlow<String?> = _running.asStateFlow()

    fun enqueue(app: App, taskId: String, scheduledAt: Long?, source: String) {
        scope.launch { execute(app, taskId, scheduledAt, source) }
    }

    suspend fun execute(app: App, taskId: String, scheduledAt: Long?, source: String) = lock.withLock {
        app.tasks.reload()
        val task = app.tasks.get(taskId) ?: return@withLock
        if (scheduledAt != null && CatchUp.alreadyRan(task, scheduledAt)) return@withLock
        if (scheduledAt != null) {
            app.scheduler.schedule(task, afterMillis = maxOf(System.currentTimeMillis(), scheduledAt))
        }
        val unlocked = app.getSystemService(UserManager::class.java).isUserUnlocked
        if (DirectBoot.deferLaunch(task.steps, unlocked)) {
            app.pending.add(task.id)
            record(
                app, task.id, task.name, source, success = false,
                "重启后尚未解锁，已排队，解锁后自动执行",
                "系统在第一次输入密码前不允许启动其他应用。",
            )
            return@withLock
        }
        val wakeLock = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgePro:task")
            .apply { acquire(30 * 60 * 1000L) }
        _running.value = taskId
        try {
            notifyOngoing(app, "正在执行：${task.name.ifBlank { "未命名任务" }}")
            val report = app.executor.run(task)
            val locked = report.detail().contains("USER_LOCKED")
            if (locked) app.pending.add(task.id)
            val summary = if (locked) "系统尚未解锁，已排队，解锁后自动执行" else report.summary
            record(app, task.id, task.name, source, report.success && !locked, summary, report.detail())
        } finally {
            _running.value = null
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun record(
        app: App,
        taskId: String,
        name: String,
        source: String,
        success: Boolean,
        summary: String,
        detail: String,
    ) {
        val start = System.currentTimeMillis()
        app.tasks.markRun(taskId, start, success)
        app.logs.add(
            LogEntry(
                time = start,
                taskId = taskId,
                taskName = name.ifBlank { "未命名任务" },
                success = success,
                summary = "[$source] $summary",
                detail = detail,
            )
        )
        if (app.settings.notifyOnFinish) notifyText(app, name.ifBlank { "未命名任务" }, summary)
    }

    private fun notifyOngoing(app: App, text: String) {
        val n = base(app, text).setOngoing(true).build()
        app.getSystemService(NotificationManager::class.java).notify(ONGOING_ID, n)
    }

    private fun notifyText(app: App, title: String, text: String) {
        val n = Notification.Builder(app, App.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(app))
            .build()
        app.getSystemService(NotificationManager::class.java).notify(resultIds.incrementAndGet(), n)
    }

    private fun base(app: App, text: String): Notification.Builder =
        Notification.Builder(app, App.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("my-edge-pro")
            .setContentText(text)
            .setContentIntent(openApp(app))

    private fun openApp(app: App): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private const val ONGOING_ID = 1001
}
