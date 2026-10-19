package com.wuhuan.edgepro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import com.wuhuan.edgepro.apps.AppRepository
import com.wuhuan.edgepro.core.CatchUp
import com.wuhuan.edgepro.data.DeviceStorage
import com.wuhuan.edgepro.data.LogStore
import com.wuhuan.edgepro.data.PendingRuns
import com.wuhuan.edgepro.data.Settings
import com.wuhuan.edgepro.data.TaskStore
import com.wuhuan.edgepro.sched.Scheduler
import com.wuhuan.edgepro.root.DaemonManager
import com.wuhuan.edgepro.sched.TaskExecutor
import com.wuhuan.edgepro.sched.TaskRunnerService

class App : Application() {

    lateinit var tasks: TaskStore
        private set
    lateinit var logs: LogStore
        private set
    lateinit var settings: Settings
        private set
    lateinit var scheduler: Scheduler
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var executor: TaskExecutor
        private set
    lateinit var daemon: DaemonManager
        private set
    lateinit var pending: PendingRuns
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        DeviceStorage.migrate(this)
        tasks = TaskStore(this)
        logs = LogStore(this)
        settings = Settings(this)
        scheduler = Scheduler(this)
        apps = AppRepository(this)
        executor = TaskExecutor(this)
        daemon = DaemonManager(packageName)
        pending = PendingRuns(this)
        createChannels()
        scheduleAndCatchUp()
        if (isUserUnlocked()) drainPendingSoon()
    }

    /** 用户输入密码后：补上解锁前读不到的旧数据，并执行排队任务。 */
    fun onUserUnlocked() {
        DeviceStorage.migrate(this)
        tasks.reload()
        scheduleAndCatchUp()
        drainPendingSoon()
    }

    fun scheduleAndCatchUp() {
        val missed = CatchUp.due(
            tasks.items.value,
            scheduler.expectedRuns(),
            System.currentTimeMillis(),
            settings.catchUpMinutes * 60_000L,
        )
        scheduler.scheduleAll(tasks.items.value)
        if (missed.isEmpty()) return
        // 若本进程正是被该次闹钟拉起，闹钟广播会先入队执行，补执行这次会被去重跳过
        Handler(Looper.getMainLooper()).postDelayed({
            missed.forEach { (task, at) -> TaskRunnerService.startOrLog(this, task.id, at, source = "补执行") }
        }, 5_000)
    }

    private fun drainPendingSoon() {
        Handler(Looper.getMainLooper()).postDelayed({
            pending.drain().forEach { id ->
                TaskRunnerService.startOrLog(this, id, scheduledAt = null, source = "解锁后")
            }
        }, 3_000)
    }

    private fun isUserUnlocked(): Boolean = getSystemService(UserManager::class.java).isUserUnlocked

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, "任务执行中", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_RESULT, "任务执行结果", NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_RESULT = "result_silent"

        lateinit var instance: App
            private set
    }
}
