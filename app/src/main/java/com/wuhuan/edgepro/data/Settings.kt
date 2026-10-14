package com.wuhuan.edgepro.data

import android.content.Context
import androidx.core.content.edit

class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** true：使用 setAlarmClock（最准时，状态栏会显示闹钟图标）；false：setExactAndAllowWhileIdle。 */
    var alarmClockMode: Boolean
        get() = prefs.getBoolean(KEY_ALARM_CLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_ALARM_CLOCK, value) }

    var notifyOnFinish: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY, value) }

    private companion object {
        const val KEY_ALARM_CLOCK = "alarm_clock_mode"
        const val KEY_NOTIFY = "notify_on_finish"
    }
}
