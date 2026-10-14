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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val root by vm.root.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
                subtitle = "加入电池优化白名单、允许后台运行、授予精确闹钟与通知权限",
                onClick = {
                    scope.launch {
                        val r = RootShell.exec(ShellCommands.optimizeSelf(context.packageName))
                        dialogText = r.combined.ifBlank { "完成" }
                        vm.rescheduleAll()
                    }
                },
            )

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
