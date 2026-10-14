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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuhuan.edgepro.core.LogEntry
import com.wuhuan.edgepro.ui.MainViewModel
import com.wuhuan.edgepro.ui.components.BackTopBar
import com.wuhuan.edgepro.ui.components.formatTime

@Composable
fun LogScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            BackTopBar("执行日志", onBack) {
                IconButton(onClick = vm::clearLogs, enabled = logs.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, "清空") }
            }
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(logs, key = { "${it.time}-${it.taskId}" }) { LogCard(it) }
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    Modifier.size(18.dp),
                    tint = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(entry.taskName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(formatTime(entry.time), style = MaterialTheme.typography.bodySmall)
            }
            Text(entry.summary, style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                Spacer(Modifier.size(8.dp))
                SelectionContainer {
                    Text(entry.detail, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
