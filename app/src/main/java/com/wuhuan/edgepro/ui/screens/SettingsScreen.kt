package com.wuhuan.edgepro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.BuildConfig
import com.wuhuan.edgepro.core.ShellCommands
import com.wuhuan.edgepro.root.RootShell
import com.wuhuan.edgepro.ui.MainViewModel
import com.wuhuan.edgepro.ui.components.BackTopBar
import com.wuhuan.edgepro.ui.components.IntField
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val root by vm.root.collectAsStateWithLifecycle()
    val daemon by vm.daemon.collectAsStateWithLifecycle()
    var daemonEnabled by remember { mutableStateOf(app.settings.daemonEnabled) }
    var catchUp by remember { mutableIntStateOf(app.settings.catchUpMinutes) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (root == true) vm.refreshDaemon() }
    var alarmClock by remember { mutableStateOf(app.settings.alarmClockMode) }
    var notify by remember { mutableStateOf(app.settings.notifyOnFinish) }
    var dialogText by remember { mutableStateOf<String?>(null) }
    val clipboard = context.getSystemService(ClipboardManager::class.java)

    Scaffold(topBar = { BackTopBar("设置", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Group("Root")
            Item(
                title = "Root 状态",
                subtitle = when (root) { null -> "检测中…"; true -> "已获取"; false -> "未获取（点击重新检测）" },
                onClick = vm::refreshRoot,
            )
            Item(
                title = "一键保活优化",
                subtitle = "授予 MIUI 自启动、加入电池优化白名单、允许后台运行、精确闹钟与通知权限",
                onClick = {
                    scope.launch {
                        val r = RootShell.exec(ShellCommands.optimizeSelf(context.packageName))
                        dialogText = r.combined.ifBlank { "完成" }
                        vm.rescheduleAll()
                    }
                },
            )

            Group("后台常驻（App 不打开也能执行）")
            val d = daemon
            SwitchItem(
                title = "Root 守护进程",
                subtitle = when {
                    root != true -> "需要 Root"
                    d == null -> "检测中…"
                    else -> buildString {
                        append(if (d.running) "运行中 (pid ${d.pid})" else "未运行")
                        append(" · 开机自启")
                        append(if (d.bootHookInstalled) "已安装" else "未安装")
                        append("\n以 root 身份常驻。重启后未解锁也会拉起；有锁屏密码时，启动应用会在输入密码后立即执行")
                    }
                },
                checked = daemonEnabled,
            ) {
                daemonEnabled = it
                vm.setDaemonEnabled(it)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("错过补执行窗口（分钟）", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "App 被杀期间错过的触发，在这个时间内被拉起会立即补执行；0 为不补执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IntField("分钟", catchUp, {
                    catchUp = it
                    app.settings.catchUpMinutes = it
                }, Modifier.width(96.dp), 0..1440)
            }
            Item("运行诊断", "检查守护进程、自启动、电池白名单、已注册闹钟，可复制结果反馈问题") {
                scope.launch { dialogText = vm.diagnose().ifBlank { "无输出（请确认已授予 Root）" } }
            }

            Group("调度")
            SwitchItem(
                title = "闹钟模式",
                subtitle = "最准时、不受省电模式影响，但状态栏会显示闹钟图标；关闭则使用普通精确闹钟",
                checked = alarmClock,
            ) {
                alarmClock = it
                app.settings.alarmClockMode = it
                vm.rescheduleAll()
            }
            SwitchItem("执行完成后通知", "每次任务执行完弹出结果通知", notify) {
                notify = it
                app.settings.notifyOnFinish = it
            }
            if (!app.scheduler.canScheduleExact()) {
                Item("精确闹钟权限未授予", "点击前往系统设置授权（或使用上方一键保活优化）") {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                    )
                }
            }

            Group("备份")
            Item("导出任务到剪贴板", "以 JSON 格式复制全部任务") {
                clipboard.setPrimaryClip(ClipData.newPlainText("tasks", app.tasks.exportJson()))
                Toast.makeText(context, "已复制 ${app.tasks.items.value.size} 个任务", Toast.LENGTH_SHORT).show()
            }
            Item("从剪贴板导入任务", "同 ID 的任务会被覆盖") {
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                val msg = runCatching { "已导入 ${app.tasks.importJson(text)} 个任务" }
                    .onSuccess { vm.rescheduleAll() }
                    .getOrElse { "导入失败：${it.message}" }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }

            Group("关于")
            Item("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    dialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { dialogText = null },
            title = { Text("执行结果") },
            text = { SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { dialogText = null }) { Text("好") } },
        )
    }
}

@Composable
private fun Group(title: String) {
    HorizontalDivider()
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
