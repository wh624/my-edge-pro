package com.wuhuan.edgepro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.core.DaemonStatus
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.core.TriggerCalculator
import com.wuhuan.edgepro.root.RootShell
import com.wuhuan.edgepro.sched.TaskRunnerService
import com.wuhuan.edgepro.sched.TaskRuns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App

    val tasks = app.tasks.items
    val logs = app.logs.items
    val running = TaskRuns.running

    private val _root = MutableStateFlow(RootShell.lastRootCheck)
    /** null 表示检测中。 */
    val root: StateFlow<Boolean?> = _root.asStateFlow()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    /** 每 15 秒刷新，用于界面上的“下次执行”时间。 */
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        refreshRoot()
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                _now.value = System.currentTimeMillis()
            }
        }
    }

    private val _daemon = MutableStateFlow<DaemonStatus?>(null)
    /** null 表示未知/检测中。 */
    val daemon: StateFlow<DaemonStatus?> = _daemon.asStateFlow()

    fun refreshRoot() {
        _root.value = null
        viewModelScope.launch {
            val ok = RootShell.checkRoot()
            _root.value = ok
            if (ok) refreshDaemon(install = app.settings.daemonEnabled)
        }
    }

    fun refreshDaemon(install: Boolean = false) {
        viewModelScope.launch {
            _daemon.value = if (install) app.daemon.ensure() else app.daemon.status()
        }
    }

    fun setDaemonEnabled(enabled: Boolean) {
        app.settings.daemonEnabled = enabled
        _daemon.value = null
        viewModelScope.launch {
            _daemon.value = if (enabled) app.daemon.ensure() else app.daemon.uninstall()
        }
    }

    suspend fun diagnose(): String = app.daemon.diagnose()

    fun task(id: String): Task? = app.tasks.get(id)

    fun save(task: Task) {
        app.tasks.upsert(task)
        app.scheduler.schedule(task)
    }

    fun setEnabled(task: Task, enabled: Boolean) = save(task.copy(enabled = enabled))

    fun delete(task: Task) {
        app.scheduler.cancel(task.id)
        app.tasks.delete(task.id)
    }

    fun move(task: Task, delta: Int) = app.tasks.move(task.id, delta)

    fun duplicate(task: Task) = save(
        Task(name = task.name + " 副本", enabled = false, trigger = task.trigger, steps = task.steps)
    )

    fun runNow(task: Task) = TaskRunnerService.start(app, task.id)

    fun nextRun(task: Task, now: Long): Long? =
        if (task.enabled && task.steps.isNotEmpty()) TriggerCalculator.nextMillis(task.trigger, now) else null

    fun clearLogs() = app.logs.clear()

    fun rescheduleAll() = app.scheduler.scheduleAll(app.tasks.items.value)
}
