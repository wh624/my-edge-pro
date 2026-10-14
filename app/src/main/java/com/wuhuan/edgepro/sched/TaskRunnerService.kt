package com.wuhuan.edgepro.sched

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.R
import com.wuhuan.edgepro.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        val scheduledAt = intent?.getLongExtra(EXTRA_SCHEDULED_AT, -1L)?.takeIf { it > 0 }
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: if (scheduledAt != null) "定时" else "手动"
        try {
            startAsForeground("准备执行任务…")
        } catch (_: Exception) {
            if (taskId != null) TaskRuns.enqueue(app, taskId, scheduledAt, source)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (taskId == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        pending.incrementAndGet()
        scope.launch {
            try {
                TaskRuns.execute(app, taskId, scheduledAt, source)
            } finally {
                pending.decrementAndGet()
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
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

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, App.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("my-edge-pro")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

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

        /** [scheduledAt] 为 null 表示手动执行，不会重排闹钟。 */
        fun start(context: Context, taskId: String, scheduledAt: Long? = null, source: String? = null) {
            val intent = Intent(context, TaskRunnerService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .apply { if (scheduledAt != null) putExtra(EXTRA_SCHEDULED_AT, scheduledAt) }
                .apply { if (source != null) putExtra(EXTRA_SOURCE, source) }
            context.startForegroundService(intent)
        }

        /** 系统拒绝启动前台服务时，改为在当前进程里直接执行，避免这次触发丢失。 */
        fun startOrLog(context: Context, taskId: String, scheduledAt: Long?, source: String) {
            try {
                start(context, taskId, scheduledAt, source)
            } catch (_: IllegalStateException) {
                TaskRuns.enqueue(context.applicationContext as App, taskId, scheduledAt, source)
            }
        }
    }
}
