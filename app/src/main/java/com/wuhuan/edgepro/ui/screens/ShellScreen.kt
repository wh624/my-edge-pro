package com.wuhuan.edgepro.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wuhuan.edgepro.root.RootShell
import com.wuhuan.edgepro.ui.components.BackTopBar
import kotlinx.coroutines.launch

private val QUICK_COMMANDS = listOf(
    "pm list users",
    "ps -A -o USER,NAME | grep -E '^u[0-9]+_a'",
    "dumpsys deviceidle whitelist",
    "dumpsys activity recents | grep 'Recent #'",
    "settings get secure default_input_method",
    "getprop ro.build.version.release",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellScreen(onBack: () -> Unit) {
    var script by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("") }
    var running by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run() {
        if (script.isBlank() || running) return
        running = true
        scope.launch {
            val start = System.currentTimeMillis()
            val r = RootShell.exec(script, timeoutSec = 120)
            output = buildString {
                appendLine("$ ${script.trim()}")
                appendLine(r.combined.ifBlank { "(无输出)" })
                append("— 退出码 ${r.code}，耗时 ${System.currentTimeMillis() - start}ms")
            }
            running = false
        }
    }

    Scaffold(topBar = { BackTopBar("Root Shell", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                label = { Text("命令（以 root 执行）") },
                minLines = 3,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QUICK_COMMANDS.forEach { cmd ->
                    AssistChip(onClick = { script = cmd }, label = { Text(cmd.take(28), fontFamily = FontFamily.Monospace) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::run, enabled = !running && script.isNotBlank()) {
                    if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("执行")
                }
            }
            Card(Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp)) {
                SelectionContainer {
                    Text(
                        output.ifEmpty { "输出会显示在这里" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
