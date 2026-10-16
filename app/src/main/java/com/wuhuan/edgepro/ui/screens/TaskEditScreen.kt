package com.wuhuan.edgepro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuhuan.edgepro.core.AppTarget
import com.wuhuan.edgepro.core.CronExpression
import com.wuhuan.edgepro.core.DailyTimes
import com.wuhuan.edgepro.core.Step
import com.wuhuan.edgepro.core.StopMode
import com.wuhuan.edgepro.core.Task
import com.wuhuan.edgepro.core.Trigger
import com.wuhuan.edgepro.core.TriggerCalculator
import com.wuhuan.edgepro.core.title
import com.wuhuan.edgepro.core.userLabel
import com.wuhuan.edgepro.data.AppJson
import com.wuhuan.edgepro.ui.MainViewModel
import com.wuhuan.edgepro.ui.Routes
import com.wuhuan.edgepro.ui.components.AppIcon
import com.wuhuan.edgepro.ui.components.BackTopBar
import com.wuhuan.edgepro.ui.components.IntField
import com.wuhuan.edgepro.ui.components.SecondsField
import com.wuhuan.edgepro.ui.components.formatTimeWithWeek
import java.time.ZonedDateTime

private val TaskSaver = Saver<androidx.compose.runtime.MutableState<Task>, String>(
    save = { AppJson.encodeToString(Task.serializer(), it.value) },
    restore = { mutableStateOf(AppJson.decodeFromString(Task.serializer(), it)) },
)

/** 正在为哪个步骤选择应用；[whitelist] 为 true 表示清理步骤的白名单。 */
private data class PickTarget(val stepIndex: Int, val whitelist: Boolean)

@Composable
fun TaskEditScreen(vm: MainViewModel, taskId: String, onBack: () -> Unit) {
    val draftState = rememberSaveable(taskId, saver = TaskSaver) {
        mutableStateOf(
            vm.task(taskId) ?: Task(
                name = if (taskId == Routes.NEW_TASK_ID) "任务 ${vm.tasks.value.size + 1}" else "",
                steps = listOf(Step.Launch()),
            )
        )
    }
    var draft by draftState
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    val triggerError = TriggerCalculator.validate(draft.trigger)

    fun updateStep(index: Int, step: Step) {
        draft = draft.copy(steps = draft.steps.toMutableList().also { it[index] = step })
    }

    fun save(runAfter: Boolean) {
        vm.save(draft)
        if (runAfter) vm.runNow(draft)
        onBack()
    }

    Scaffold(
        topBar = {
            BackTopBar(if (taskId == Routes.NEW_TASK_ID) "新建任务" else "编辑任务", onBack) {
                IconButton(onClick = { save(runAfter = true) }, enabled = triggerError == null && draft.steps.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, "保存并执行")
                }
                IconButton(onClick = { save(runAfter = false) }, enabled = triggerError == null) {
                    Icon(Icons.Default.Check, "保存")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("任务名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", Modifier.weight(1f))
                Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })
            }

            SectionTitle("触发规则")
            TriggerEditor(draft.trigger, triggerError) { draft = draft.copy(trigger = it) }

            SectionTitle("执行步骤（按顺序）")
            draft.steps.forEachIndexed { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    isFirst = index == 0,
                    isLast = index == draft.steps.lastIndex,
                    onChange = { updateStep(index, it) },
                    onMove = { delta ->
                        val list = draft.steps.toMutableList()
                        val target = index + delta
                        if (target in list.indices) {
                            list[index] = list[target].also { list[target] = list[index] }
                            draft = draft.copy(steps = list)
                        }
                    },
                    onCopy = {
                        val list = draft.steps.toMutableList()
                        list.add(index + 1, step)
                        draft = draft.copy(steps = list)
                    },
                    onDelete = { draft = draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != index }) },
                    onPick = { whitelist -> picking = PickTarget(index, whitelist) },
                )
            }
            AddStepButton { draft = draft.copy(steps = draft.steps + it) }
            Spacer(Modifier.size(48.dp))
        }
    }

    picking?.let { target ->
        val step = draft.steps.getOrNull(target.stepIndex)
        val initial = when (step) {
            is Step.Launch -> step.apps
            is Step.Stop -> step.apps
            is Step.CleanBackground -> step.whitelist
            else -> emptyList()
        }
        AppPickerDialog(
            title = if (target.whitelist) "选择白名单" else "选择应用",
            initial = initial,
            onDismiss = { picking = null },
            onConfirm = { selected ->
                when (step) {
                    is Step.Launch -> updateStep(target.stepIndex, step.copy(apps = selected))
                    is Step.Stop -> updateStep(target.stepIndex, step.copy(apps = selected))
                    is Step.CleanBackground -> updateStep(target.stepIndex, step.copy(whitelist = selected))
                    else -> Unit
                }
                picking = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerEditor(trigger: Trigger, error: String?, onChange: (Trigger) -> Unit) {
    val isCron = trigger is Trigger.Cron
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !isCron,
            onClick = { if (isCron) onChange(Trigger.Daily("065000")) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("每日定时") }
        SegmentedButton(
            selected = isCron,
            onClick = { if (!isCron) onChange(Trigger.Cron(dailyToCron(trigger as Trigger.Daily))) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Cron 表达式") }
    }
    when (trigger) {
        is Trigger.Daily -> {
            MultiValueList(
                initial = DailyTimes.rawEntries(trigger.times).ifEmpty { listOf("065000") },
                label = { "时间 ${it + 1}" },
                placeholder = "065000",
                addLabel = "添加时间",
                supporting = "格式 065000、0650 或 06:50:00，到点都执行下面的步骤",
                onInvalid = { DailyTimes.parseOrNull(it) == null },
                onChange = { onChange(trigger.copy(times = it.joinToString(","))) },
            )
            Text("重复（不选为每天）", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TriggerCalculator.WEEKDAY_NAMES.forEachIndexed { i, name ->
                    val day = i + 1
                    val selected = day in trigger.weekdays
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange(trigger.copy(weekdays = if (selected) trigger.weekdays - day else trigger.weekdays + day))
                        },
                        label = { Text("周$name") },
                    )
                }
                FilterChip(
                    selected = trigger.weekdays == setOf(1, 2, 3, 4, 5),
                    onClick = { onChange(trigger.copy(weekdays = setOf(1, 2, 3, 4, 5))) },
                    label = { Text("工作日") },
                )
            }
        }
        is Trigger.Cron -> {
            MultiValueList(
                initial = TriggerCalculator.cronLines(trigger.expression).ifEmpty { listOf("0 50 6 * * *") },
                label = { "Cron ${it + 1}" },
                placeholder = "0 50 6 * * *",
                addLabel = "添加一条 Cron",
                supporting = "秒 分 时 日 月 周，或 分 时 日 月 周。支持 * , - / 以及 MON-SUN",
                monospace = true,
                onInvalid = { CronExpression.parseOrNull(it) == null },
                onChange = { onChange(trigger.copy(expression = it.joinToString("\n"))) },
            ) { add ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "0 50 6 * * *" to "每天 06:50",
                        "0 0 8 * * MON-FRI" to "工作日 8 点",
                        "0 */30 9-18 * * *" to "9-18 点每半小时",
                        "0 0 0 1 * *" to "每月 1 号",
                    ).forEach { (expr, desc) ->
                        AssistChip(onClick = { add(expr) }, label = { Text(desc) })
                    }
                }
            }
        }
    }
    if (error == null) {
        val upcoming = remember(trigger) { TriggerCalculator.nextTimes(trigger, ZonedDateTime.now(), 5) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("接下来的触发时间", style = MaterialTheme.typography.labelMedium)
                if (upcoming.isEmpty()) Text("未来 5 年内不会触发", color = MaterialTheme.colorScheme.error)
                upcoming.forEach {
                    Text(formatTimeWithWeek(it.toInstant().toEpochMilli()), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun MultiValueList(
    initial: List<String>,
    label: (Int) -> String,
    placeholder: String,
    addLabel: String,
    supporting: String,
    onInvalid: (String) -> Boolean,
    onChange: (List<String>) -> Unit,
    monospace: Boolean = false,
    extras: @Composable ((String) -> Unit) -> Unit = {},
) {
    val rows = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    fun commit() = onChange(rows.map { it.trim() }.filter { it.isNotEmpty() })
    fun add(value: String) {
        if (rows.any { it.trim() == value }) return
        if (rows.size == 1 && rows[0].isBlank()) rows[0] = value else rows.add(value)
        commit()
    }
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in rows.indices) return
        val current = rows[index]
        rows[index] = rows[target]
        rows[target] = current
        commit()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, value ->
            val invalid = value.isNotBlank() && onInvalid(value.trim())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { rows[index] = it; commit() },
                    label = { Text(label(index)) },
                    placeholder = { Text(placeholder) },
                    isError = invalid,
                    supportingText = { Text(if (invalid) "无法识别" else if (index == rows.lastIndex) supporting else "") },
                    singleLine = true,
                    textStyle = if (monospace) {
                        MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    modifier = Modifier.weight(1f),
                )
                if (rows.size > 1) {
                    IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "上移", Modifier.size(20.dp))
                    }
                    IconButton(onClick = { move(index, 1) }, enabled = index < rows.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "下移", Modifier.size(20.dp))
                    }
                    IconButton(onClick = { rows.removeAt(index); commit() }) {
                        Icon(Icons.Default.Delete, "删除这条定时")
                    }
                }
            }
        }
        OutlinedButton(onClick = { rows.add("") }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(addLabel)
        }
        extras(::add)
    }
}

private fun dailyToCron(daily: Trigger.Daily): String {
    val times = DailyTimes.parseOrNull(daily.times)?.ifEmpty { null } ?: listOf(java.time.LocalTime.of(6, 50))
    val dow = if (daily.weekdays.isEmpty() || daily.weekdays.size == 7) "*"
    else daily.weekdays.sorted().joinToString(",") { (it % 7).toString() }
    return times.joinToString("\n") { "${it.second} ${it.minute} ${it.hour} * * $dow" }
}

private fun stepIcon(step: Step): ImageVector = when (step) {
    is Step.Launch -> Icons.Default.PlayArrow
    is Step.Stop -> Icons.Default.Stop
    is Step.CleanBackground -> Icons.Default.CleaningServices
    is Step.Shell -> Icons.Default.Code
    is Step.Delay -> Icons.Default.Timer
}

@Composable
private fun StepCard(
    index: Int,
    step: Step,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (Step) -> Unit,
    onMove: (Int) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onPick: (whitelist: Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stepIcon(step), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("${index + 1}. ${step.title()}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) { Icon(Icons.Default.KeyboardArrowUp, "上移") }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) { Icon(Icons.Default.KeyboardArrowDown, "下移") }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "复制步骤") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    is Step.Launch -> {
                        AppTargetChips(step.apps) { onChange(step.copy(apps = step.apps - it)) }
                        OutlinedButton(onClick = { onPick(false) }) {
                            Icon(Icons.Default.Apps, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择应用（已选 ${step.apps.size}）")
                        }
                        SecondsField("每个应用之间的间隔（秒）", step.intervalMs, { onChange(step.copy(intervalMs = it)) }, Modifier.fillMaxWidth())
                    }
                    is Step.Stop -> {
                        AppTargetChips(step.apps) { onChange(step.copy(apps = step.apps - it)) }
                        OutlinedButton(onClick = { onPick(false) }) {
                            Icon(Icons.Default.Apps, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择应用（已选 ${step.apps.size}）")
                        }
                        StopModeSelector(step.mode) { onChange(step.copy(mode = it)) }
                    }
                    is Step.CleanBackground -> {
                        Text(
                            "停止所有正在运行的应用，自动排除：当前桌面、输入法、本应用、系统关键进程。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        StopModeSelector(step.mode) { onChange(step.copy(mode = it)) }
                        SwitchRow("包含分身/其他用户空间", step.allUsers) { onChange(step.copy(allUsers = it)) }
                        SwitchRow("包含系统应用（谨慎）", step.includeSystem) { onChange(step.copy(includeSystem = it)) }
                        Text("白名单（不会被清理）", style = MaterialTheme.typography.bodySmall)
                        AppTargetChips(step.whitelist) { onChange(step.copy(whitelist = step.whitelist - it)) }
                        OutlinedButton(onClick = { onPick(true) }) { Text("选择白名单（${step.whitelist.size}）") }
                    }
                    is Step.Shell -> {
                        OutlinedTextField(
                            value = step.script,
                            onValueChange = { onChange(step.copy(script = it)) },
                            label = { Text("Shell 脚本（以 root 执行）") },
                            placeholder = { Text("例如：input keyevent KEYCODE_HOME") },
                            minLines = 3,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        IntField("超时（秒）", step.timeoutSec, { onChange(step.copy(timeoutSec = it)) }, Modifier.fillMaxWidth(), 1..3600)
                    }
                    is Step.Delay -> {
                        IntField("等待（秒）", step.seconds, { onChange(step.copy(seconds = it)) }, Modifier.fillMaxWidth(), 0..86400)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StopModeSelector(mode: StopMode, onChange: (StopMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == StopMode.FORCE_STOP,
            onClick = { onChange(StopMode.FORCE_STOP) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("强行停止") }
        SegmentedButton(
            selected = mode == StopMode.KILL,
            onClick = { onChange(StopMode.KILL) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("温和结束") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppTargetChips(apps: List<AppTarget>, onRemove: (AppTarget) -> Unit) {
    if (apps.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEach { app ->
            AssistChip(
                onClick = { onRemove(app) },
                label = { Text(if (app.userId == 0) app.label else "${app.label} · ${userLabel(app.userId)}") },
                leadingIcon = { AppIcon(app.packageName, 20.dp) },
                trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun AddStepButton(onAdd: (Step) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("添加步骤")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Step.Launch(), Step.Stop(), Step.CleanBackground(), Step.Shell(), Step.Delay()).forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title()) },
                    leadingIcon = { Icon(stepIcon(s), null) },
                    onClick = { onAdd(s); open = false },
                )
            }
        }
    }
}