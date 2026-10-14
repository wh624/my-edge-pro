package com.wuhuan.edgepro.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.core.TriggerCalculator
import com.wuhuan.edgepro.data.Settings
import com.wuhuan.edgepro.ui.MainActivity

class Scheduler(private val context: Context, private val settings: Settings) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 每个任务最近一次注册的触发时间，进程被杀后用来判断错过了哪些触发。 */
    private val expectedPrefs = context.getSharedPreferences("expected_runs", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAll(tasks: List<Task>) = tasks.forEach { schedule(it) }

    fun expectedRuns(): Map<String, Long> =
        expectedPrefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /**
     * 为任务注册下一次闹钟。[afterMillis] 之后（不含）才会触发，
     * 用于在执行完毕后跳过本次触发时间。返回下次触发的毫秒时间戳。
     */
    fun schedule(task: Task, afterMillis: Long = System.currentTimeMillis()): Long? {
        cancel(task.id)
        if (!task.enabled || task.steps.isEmpty()) return null
        val next = TriggerCalculator.nextMillis(task.trigger, afterMillis) ?: return null
        val operation = alarmIntent(task.id, next)
        try {
            if (settings.alarmClockMode && canScheduleExact()) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), operation)
            } else if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation)
        }
        expectedPrefs.edit { putLong(task.id, next) }
        return next
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(alarmIntent(taskId, 0))
        expectedPrefs.edit { remove(taskId) }
    }

    private fun alarmIntent(taskId: String, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RUN)
            .setData("edgepro://task/$taskId".toUri())
            .putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
