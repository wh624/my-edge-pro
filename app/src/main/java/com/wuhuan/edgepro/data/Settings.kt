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

    /** 打开 App 且有 root 时自动安装并启动 root 守护进程。 */
    var daemonEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAEMON, true)
        set(value) = prefs.edit { putBoolean(KEY_DAEMON, value) }

    /** 进程被杀期间错过的触发，在多少分钟内仍补执行；0 表示不补执行。 */
    var catchUpMinutes: Int
        get() = prefs.getInt(KEY_CATCH_UP, 30)
        set(value) = prefs.edit { putInt(KEY_CATCH_UP, value) }

    private companion object {
        const val KEY_ALARM_CLOCK = "alarm_clock_mode"
        const val KEY_NOTIFY = "notify_on_finish"
        const val KEY_DAEMON = "daemon_enabled"
        const val KEY_CATCH_UP = "catch_up_minutes"
    }
}
