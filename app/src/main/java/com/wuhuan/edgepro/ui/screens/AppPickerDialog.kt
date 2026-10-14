package com.wuhuan.edgepro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wuhuan.edgepro.App
import com.wuhuan.edgepro.apps.InstalledApp
import com.wuhuan.edgepro.core.AppTarget
import com.wuhuan.edgepro.core.UserSpace
import com.wuhuan.edgepro.core.userLabel
import com.wuhuan.edgepro.ui.components.AppIcon
import com.wuhuan.edgepro.ui.components.BackTopBar

private fun key(pkg: String, userId: Int) = "$pkg@$userId"

/** 全屏应用选择器：按用户空间分 Tab，支持搜索、显示系统应用、多选。 */
@Composable
fun AppPickerDialog(
    title: String,
    initial: List<AppTarget>,
    onDismiss: () -> Unit,
    onConfirm: (List<AppTarget>) -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val selected = remember {
        mutableStateMapOf<String, AppTarget>().apply { initial.forEach { put(key(it.packageName, it.userId), it) } }
    }
    var users by remember { mutableStateOf<List<UserSpace>?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    val appsByUser = remember { mutableStateMapOf<Int, List<InstalledApp>>() }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { users = app.apps.loadUsers() }
    val currentUser = users?.getOrNull(tab)
    LaunchedEffect(currentUser?.id) {
        val id = currentUser?.id ?: return@LaunchedEffect
        if (id !in appsByUser) appsByUser[id] = app.apps.loadApps(id)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                BackTopBar(title, onDismiss) {
                    TextButton(onClick = { onConfirm(selected.values.toList()) }) { Text("确定（${selected.size}）") }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val us = users
                if (us == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
                if (us.size > 1) {
                    PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                        us.forEachIndexed { i, u ->
                            Tab(
                                selected = tab == i,
                                onClick = { tab = i },
                                text = { Text("${userLabel(u.id)}${if (u.name.isNotBlank() && u.id != 0) " · ${u.name}" else ""}") },
                            )
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索名称或包名") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                    Text("系统", style = MaterialTheme.typography.bodySmall)
                }
                val userId = currentUser?.id ?: 0
                val list = appsByUser[userId]
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
                val q = query.trim().lowercase()
                val filtered = list.filter { (showSystem || !it.isSystem || key(it.packageName, userId) in selected) }
                    .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                    .sortedByDescending { key(it.packageName, userId) in selected }
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有应用（未获取 Root 时只能读取主空间）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { key(it.packageName, userId) }) { item ->
                        val k = key(item.packageName, userId)
                        val checked = k in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(k)
                                    else selected[k] = AppTarget(item.packageName, userId, item.label)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(item.packageName, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.packageName + if (item.isSystem) " · 系统" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}
