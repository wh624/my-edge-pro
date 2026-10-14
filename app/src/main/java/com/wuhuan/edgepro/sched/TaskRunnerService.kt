package com.wuhuan.edgepro.sched

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.R
import com.wuhuan.edgepro.core.CatchUp
import com.wuhuan.edgepro.core.LogEntry
import com.wuhuan.edgepro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** 前台服务：串行执行任务、写日志、重排下一次闹钟。 */
class TaskRunnerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = AtomicInteger(0)
    @Volatile
    private var lastStartId = 0
    private val app get() = application as App

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startAsForeground("准备执行任务…")
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (taskId == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, -1L).takeIf { it > 0 }
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: if (scheduledAt != null) "定时" else "手动"
        pending.incrementAndGet()
        scope.launch {
            try {
                runTask(taskId, scheduledAt, source)
            } finally {
                pending.decrementAndGet()
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runTask(taskId: String, scheduledAt: Long?, source: String) = executionLock.withLock {
        app.tasks.reload()
        val task = app.tasks.get(taskId) ?: return@withLock
        if (scheduledAt != null && CatchUp.alreadyRan(task, scheduledAt)) return@withLock
        if (scheduledAt != null) {
            // 先排好下一次，防止执行过程中进程被杀导致断档
            app.scheduler.schedule(task, afterMillis = maxOf(System.currentTimeMillis(), scheduledAt))
        }
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgePro:task")
            .apply { acquire(30 * 60 * 1000L) }
        _running.value = taskId
        try {
            updateNotification("正在执行：${task.name.ifBlank { "未命名任务" }}")
            val start = System.currentTimeMillis()
            val report = app.executor.run(task)
            app.tasks.markRun(task.id, start, report.success)
            app.logs.add(
                LogEntry(
                    time = start,
                    taskId = task.id,
                    taskName = task.name.ifBlank { "未命名任务" },
                    success = report.success,
                    summary = "[$source] " + report.summary,
                    detail = report.detail(),
                )
            )
            if (app.settings.notifyOnFinish) notifyResult(task.name, report)
        } finally {
            _running.value = null
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    /** stopSelf(startId) 只在没有更新的 start 请求时才会真正停止，避免误杀刚提交的任务。 */
    private fun stopIfIdle() {
        if (pending.get() == 0 && stopSelfResult(lastStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun startAsForeground(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, App.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Edge 定时")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notifyResult(name: String, report: ExecutionReport) {
        val n = Notification.Builder(this, App.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${name.ifBlank { "未命名任务" }} ${if (report.success) "执行成功" else "执行失败"}")
            .setContentText(report.summary)
            .setStyle(Notification.BigTextStyle().bigText(report.summary))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(resultIdSeq.incrementAndGet(), n)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_SCHEDULED_AT = "scheduled_at"
        private const val EXTRA_SOURCE = "source"
        private const val NOTIFICATION_ID = 1001
        private val resultIdSeq = AtomicInteger(2000)
        private val executionLock = Mutex()

        private val _running = MutableStateFlow<String?>(null)
        /** 当前正在执行的任务 id。 */
        val running: StateFlow<String?> = _running.asStateFlow()

        /** [scheduledAt] 为 null 表示手动执行，不会重排闹钟。 */
        fun start(context: Context, taskId: String, scheduledAt: Long? = null, source: String? = null) {
            val intent = Intent(context, TaskRunnerService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .apply { if (scheduledAt != null) putExtra(EXTRA_SCHEDULED_AT, scheduledAt) }
                .apply { if (source != null) putExtra(EXTRA_SOURCE, source) }
            context.startForegroundService(intent)
        }

        /** 后台场景使用：系统拒绝启动前台服务时重排闹钟并写一条失败日志，而不是静默丢失。 */
        fun startOrLog(context: Context, taskId: String, scheduledAt: Long, source: String) {
            try {
                start(context, taskId, scheduledAt, source)
            } catch (e: IllegalStateException) {
                val app = context.applicationContext as App
                val task = app.tasks.get(taskId) ?: return
                app.scheduler.schedule(task, afterMillis = maxOf(System.currentTimeMillis(), scheduledAt))
                app.logs.add(
                    LogEntry(
                        time = System.currentTimeMillis(),
                        taskId = task.id,
                        taskName = task.name.ifBlank { "未命名任务" },
                        success = false,
                        summary = "[$source] 系统拒绝后台启动执行服务",
                        detail = "${e.message}\n请在设置中开启「Root 守护进程」并执行「一键保活优化」。",
                    )
                )
            }
        }
    }
}
