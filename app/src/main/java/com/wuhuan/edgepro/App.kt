package com.wuhuan.edgepro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wuhuan.edgepro.apps.AppRepository
import com.wuhuan.edgepro.data.LogStore
import com.wuhuan.edgepro.data.Settings
import com.wuhuan.edgepro.data.TaskStore
import com.wuhuan.edgepro.sched.Scheduler
import com.wuhuan.edgepro.sched.TaskExecutor

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

    override fun onCreate() {
        super.onCreate()
        instance = this
        tasks = TaskStore(this)
        logs = LogStore(this)
        settings = Settings(this)
        scheduler = Scheduler(this, settings)
        apps = AppRepository(this)
        executor = TaskExecutor(this)
        createChannels()
        scheduler.scheduleAll(tasks.items.value)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, "任务执行中", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_RESULT, "任务执行结果", NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_RESULT = "result"

        lateinit var instance: App
            private set
    }
}
