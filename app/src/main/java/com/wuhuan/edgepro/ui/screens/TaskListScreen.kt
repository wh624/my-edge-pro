package com.wuhuan.edgepro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.core.TriggerCalculator
import com.wuhuan.edgepro.core.summary
import com.wuhuan.edgepro.ui.MainViewModel
import com.wuhuan.edgepro.ui.components.formatRelative
import com.wuhuan.edgepro.ui.components.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: MainViewModel,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onLogs: () -> Unit,
    onShell: () -> Unit,
    onSettings: () -> Unit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val root by vm.root.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("my-edge-pro") },
                actions = {
                    IconButton(onClick = onShell) { Icon(Icons.Default.Terminal, "Shell") }
                    IconButton(onClick = onLogs) { Icon(Icons.Default.History, "日志") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建任务") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RootBanner(root, vm::refreshRoot) }
            if (tasks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "还没有任务\n点击右下角「新建任务」开始",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                TaskCard(
                    task = task,
                    nextRun = vm.nextRun(task, now),
                    now = now,
                    isRunning = running == task.id,
                    isFirst = index == 0,
                    isLast = index == tasks.lastIndex,
                    onClick = { onEdit(task.id) },
                    onToggle = { vm.setEnabled(task, it) },
                    onRun = { vm.runNow(task) },
                    onShortcut = { vm.createShortcut(task) },
                    onMove = { vm.move(task, it) },
                    onCopy = { vm.duplicate(task) },
                    onDelete = { deleting = task },
                )
            }
        }
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除任务") },
            text = { Text("确定删除「${task.name.ifBlank { "未命名任务" }}」？已经加到桌面的快捷方式会失效。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(task); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RootBanner(root: Boolean?, onRetry: () -> Unit) {
    val (text, color) = when (root) {
        null -> "正在检测 Root 权限…" to MaterialTheme.colorScheme.surfaceVariant
        true -> "Root 权限已获取" to MaterialTheme.colorScheme.secondaryContainer
        false -> "未获取 Root 权限，任务将无法执行" to MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (root == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(if (root) Icons.Default.CheckCircle else Icons.Default.Error, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (root == false) TextButton(onClick = onRetry) { Text("重新检测") }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    nextRun: Long?,
    now: Long,
    isRunning: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onShortcut: () -> Unit,
    onMove: (Int) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val invalid = TriggerCalculator.validate(task.trigger)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.name.ifBlank { "未命名任务" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        TriggerCalculator.describe(task.trigger),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Switch(checked = task.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.size(4.dp))
            val nextText = when {
                invalid != null -> "规则无效：$invalid"
                task.steps.isEmpty() -> "没有步骤"
                !task.enabled -> "已停用"
                nextRun == null -> "不会再触发"
                else -> "下次：${formatTime(nextRun)}（${formatRelative(nextRun, now)}）"
            }
            Text(
                nextText,
                style = MaterialTheme.typography.bodySmall,
                color = if (invalid != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.steps.isNotEmpty()) {
                Text(
                    task.steps.joinToString(" → ") { it.summary() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            task.lastRunAt?.let {
                Text(
                    "上次：${formatTime(it)} ${if (task.lastSuccess == true) "成功" else "失败"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.lastSuccess == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRun, enabled = !isRunning && task.steps.isNotEmpty()) {
                    if (isRunning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("执行中")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("立即执行")
                    }
                }
                TextButton(onClick = onShortcut) {
                    Icon(Icons.Default.AddToHomeScreen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("快捷方式")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, "上移", Modifier.size(20.dp))
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, "下移", Modifier.size(20.dp))
                }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "复制任务", Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp)) }
            }
        }
    }
}
