package com.wuhuan.edgepro.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.core.net.toUri
import com.wuhuan.edgepro.core.AlarmPlan
import com.wuhuan.edgepro.core.Task
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class Scheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val gate = Any()
    private val generations = ConcurrentHashMap<String, AtomicInteger>()

    /** 每个任务已经挂上的触发时间。进程被杀后用来判断错过了哪一次。 */
    private val expectedPrefs = context.getSharedPreferences("expected_runs", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAll(tasks: List<Task>) = tasks.forEach { schedule(it) }

    /**
     * 补执行用：每个任务已经错过的最近一次。还没到的闹钟不在这里，避免把未来的时间当成漏执行。
     */
    fun expectedRuns(): Map<String, Long> {
        val now = System.currentTimeMillis()
        return expectedPrefs.all.mapNotNull { (taskId, value) ->
            AlarmPlan.catchUpMarker(parseArmed(value), now)?.let { taskId to it }
        }.toMap()
    }

    /**
     * 为任务挂上下一批闹钟。[afterMillis] 之后（不含）才会触发。
     * 返回最近一次触发的毫秒时间戳。
     *
     * 每个触发时间用不同的 PendingIntent。先取消不要的，再注册要留下的。
     * 小米上「先注册再 cancel」会把刚挂上的闹钟一起清掉，所以取消必须在注册之前。
     * 一秒后再注册一遍，避开取消操作滞后生效。
     *
     * 用 setAlarmClock：setExactAndAllowWhileIdle 在 Doze / 小米省电下有最小间隔，
     * 同一任务靠得近的下一次会被推迟或丢掉。showIntent 传另一条指向同一接收器的广播。
     * 传 null 时 HyperOS 到点不投递；传 Activity 时会把界面拉起来并点亮屏幕。
     */
    fun schedule(task: Task, afterMillis: Long = System.currentTimeMillis()): Long? = synchronized(gate) {
        val gen = bump(task.id)
        val desired = if (task.enabled && task.steps.isNotEmpty()) {
            AlarmPlan.upcomingMillis(task.trigger, afterMillis)
        } else {
            emptyList()
        }
        val previous = readArmed(task.id)
        if (desired.isEmpty()) {
            expectedPrefs.edit(commit = true) { remove(task.id) }
        } else {
            expectedPrefs.edit(commit = true) { putString(task.id, desired.joinToString(",")) }
        }
        for (at in previous - desired.toSet()) disarm(task.id, at)
        disarmLegacy(task.id)
        if (desired.isEmpty()) return@synchronized null
        armAll(task.id, desired)
        val taskId = task.id
        handler.postDelayed({
            synchronized(gate) {
                if (generationOf(taskId) != gen) return@synchronized
                armAll(taskId, desired)
            }
        }, REARM_DELAY_MS)
        desired.first()
    }

    fun cancel(taskId: String) {
        synchronized(gate) {
            bump(taskId)
            for (at in readArmed(taskId)) disarm(taskId, at)
            disarmLegacy(taskId)
            expectedPrefs.edit(commit = true) { remove(taskId) }
        }
    }

    /** 从晚到早注册。系统若只保留最后一条，留下的是最近的那次。 */
    private fun armAll(taskId: String, times: List<Long>) {
        for (at in times.sortedDescending()) arm(taskId, at)
    }

    private fun arm(taskId: String, triggerAt: Long) {
        val operation = pending(taskId, triggerAt, show = false)
        val shown = pending(taskId, triggerAt, show = true)
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, shown), operation)
            return
        } catch (_: Exception) {
            // 没有精确闹钟权限时退回普通精确闹钟
        }
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: Exception) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun disarm(taskId: String, triggerAt: Long) {
        alarmManager.cancel(pending(taskId, triggerAt, show = false))
        alarmManager.cancel(pending(taskId, triggerAt, show = true))
    }

    /** 旧版本一个任务只有一条闹钟，data 里没有触发时间。升级后把它清掉，避免和新闹钟叠在一起。 */
    private fun disarmLegacy(taskId: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RUN)
            .setData("edgepro://task/$taskId".toUri())
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
    }

    private fun pending(taskId: String, triggerAt: Long, show: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RUN)
            .setData("edgepro://${if (show) "show" else "run"}/$taskId/$triggerAt".toUri())
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, triggerAt, if (show) SHOW_SALT else 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun bump(taskId: String): Int =
        generations.getOrPut(taskId) { AtomicInteger() }.incrementAndGet()

    private fun generationOf(taskId: String): Int = generations[taskId]?.get() ?: 0

    private fun readArmed(taskId: String): Set<Long> = parseArmed(expectedPrefs.all[taskId])

    private fun parseArmed(value: Any?): Set<Long> = when (value) {
        is Long -> setOf(value)
        is String -> value.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        else -> emptySet()
    }

    private fun requestCode(taskId: String, triggerAt: Long, salt: Int): Int {
        var hash = taskId.hashCode()
        hash = 31 * hash + (triggerAt xor (triggerAt ushr 32)).toInt()
        hash = hash xor salt
        return if (hash == 0) 1 else hash
    }

    private companion object {
        const val REARM_DELAY_MS = 1000L
        const val SHOW_SALT = 0x5A5A5A5A
    }
}
