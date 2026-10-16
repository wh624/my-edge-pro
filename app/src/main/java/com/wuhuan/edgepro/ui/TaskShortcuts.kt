package com.wuhuan.edgepro.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.sched.TaskRunnerService

/** 把单个任务固定到桌面。点图标后由 [ShortcutRunActivity] 立刻执行该任务。 */
object TaskShortcuts {

    const val ACTION_RUN = "com.wuhuan.edgepro.action.RUN_TASK"
    const val EXTRA_TASK_ID = "task_id"

    fun label(task: Task): String = task.name.ifBlank { "未命名任务" }

    /** @return 给用户看的结果说明。系统通常还会再弹一次确认框。 */
    fun pin(context: Context, task: Task): String {
        val name = label(task)
        val shortcut = build(context, task)
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return legacyPin(context, task, shortcut)
        }
        val accepted = runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }.getOrDefault(false)
        return if (accepted) "请在弹窗中确认，把「$name」加到桌面" else "系统没有接受「$name」的快捷方式请求"
    }

    /** 任务改名后，刷新已经固定在桌面上的图标。没固定过的任务不动。 */
    fun syncPinned(context: Context, task: Task) {
        runCatching {
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            if (pinned.any { it.id == task.id }) {
                ShortcutManagerCompat.updateShortcuts(context, listOf(build(context, task)))
            }
        }
    }

    fun disable(context: Context, taskId: String) {
        runCatching { ShortcutManagerCompat.disableShortcuts(context, listOf(taskId), "任务已删除") }
    }

    private fun build(context: Context, task: Task): ShortcutInfoCompat {
        val name = label(task)
        val launch = Intent(context, ShortcutRunActivity::class.java)
            .setAction(ACTION_RUN)
            .putExtra(EXTRA_TASK_ID, task.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return ShortcutInfoCompat.Builder(context, task.id)
            .setShortLabel(name.take(20))
            .setLongLabel(name.take(48))
            .setIcon(IconCompat.createWithBitmap(icon(name, task.id)))
            .setIntent(launch)
            .build()
    }

    private fun legacyPin(context: Context, task: Task, shortcut: ShortcutInfoCompat): String {
        val add = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut.intent)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, label(task))
            .putExtra(Intent.EXTRA_SHORTCUT_ICON, icon(label(task), task.id))
            .putExtra("duplicate", false)
        context.sendBroadcast(add)
        return "已请求把「${label(task)}」加到桌面"
    }

    private fun icon(name: String, taskId: String): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLORS[Math.floorMod(taskId.hashCode(), COLORS.size)]
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.46f
        val letter = name.take(1)
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        canvas.drawText(letter, size / 2f, size / 2f - (bounds.top + bounds.bottom) / 2f, paint)
        return bitmap
    }

    private val COLORS = intArrayOf(
        0xFF1E3A8A.toInt(),
        0xFF0F766E.toInt(),
        0xFF9A3412.toInt(),
        0xFF6D28D9.toInt(),
        0xFF9F1239.toInt(),
        0xFF3F6212.toInt(),
        0xFF1D4ED8.toInt(),
        0xFF92400E.toInt(),
    )
}

/** 桌面图标的落点。不进入最近任务，点一下就执行对应任务然后关掉。 */
class ShortcutRunActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != TaskShortcuts.ACTION_RUN) {
            finish()
            return
        }
        val id = intent.getStringExtra(TaskShortcuts.EXTRA_TASK_ID)
        val task = id?.let { (application as App).tasks.get(it) }
        when {
            task == null -> toast("任务不存在或已删除")
            task.steps.isEmpty() -> toast("「${TaskShortcuts.label(task)}」没有可执行的步骤")
            else -> {
                TaskRunnerService.startOrLog(this, task.id, scheduledAt = null, source = "快捷方式")
                toast("正在执行：${TaskShortcuts.label(task)}")
            }
        }
        finish()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
