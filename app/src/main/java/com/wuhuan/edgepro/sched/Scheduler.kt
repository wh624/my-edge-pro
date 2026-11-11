package com.wuhuan.edgepro.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.wuhuan.edgepro.core.AlarmPlan
import com.wuhuan.edgepro.core.Task

class Scheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

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
     * 每次触发用不同的 PendingIntent。先把要保留的挂上，再取消多余的，
     * 避免小米上「先 cancel 再 set 同一个 PendingIntent」把刚挂上的闹钟一起取消。
     * 用 setAlarmClock 是因为 setExactAndAllowWhileIdle 在 Doze / 小米省电下有最小间隔，
     * 同一任务的下一次会被推迟或直接丢掉。showIntent 必须是 null：传 Activity 时，
     * MIUI 到点会把界面拉起来并点亮屏幕。
     */
    fun schedule(task: Task, afterMillis: Long = System.currentTimeMillis()): Long? {
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
        // 从晚到早注册，系统若只留最后一条，留下的是最近的那次。
        for (at in desired.sortedDescending()) arm(task.id, at)
        for (at in previous - desired.toSet()) disarm(task.id, at)
        disarmLegacy(task.id)
        return desired.firstOrNull()
    }

    fun cancel(taskId: String) {
        for (at in readArmed(taskId)) disarm(taskId, at)
        disarmLegacy(taskId)
        expectedPrefs.edit(commit = true) { remove(taskId) }
    }

    private fun arm(taskId: String, triggerAt: Long) {
        val operation = operation(taskId, triggerAt)
        try {
            if (canScheduleExact()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun disarm(taskId: String, triggerAt: Long) {
        alarmManager.cancel(operation(taskId, triggerAt))
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

    private fun operation(taskId: String, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RUN)
            .setData("edgepro://run/$taskId/$triggerAt".toUri())
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, triggerAt),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun readArmed(taskId: String): Set<Long> = parseArmed(expectedPrefs.all[taskId])

    private fun parseArmed(value: Any?): Set<Long> = when (value) {
        is Long -> setOf(value)
        is String -> value.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        else -> emptySet()
    }

    private fun requestCode(taskId: String, triggerAt: Long): Int {
        var hash = taskId.hashCode()
        hash = 31 * hash + (triggerAt xor (triggerAt ushr 32)).toInt()
        return if (hash == 0) 1 else hash
    }
}
