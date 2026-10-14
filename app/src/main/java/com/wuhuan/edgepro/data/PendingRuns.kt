package com.wuhuan.edgepro.data

import android.content.Context
import androidx.core.content.edit

/** 因重启后尚未解锁而延后的任务。存在设备加密存储里，解锁广播到来时取走。 */
class PendingRuns(context: Context) {

    private val prefs = context.getSharedPreferences("pending_runs", Context.MODE_PRIVATE)

    @Synchronized
    fun add(taskId: String) {
        prefs.edit { putBoolean(taskId, true) }
    }

    @Synchronized
    fun drain(): List<String> {
        val ids = prefs.all.keys.toList()
        if (ids.isNotEmpty()) prefs.edit { clear() }
        return ids
    }
}
