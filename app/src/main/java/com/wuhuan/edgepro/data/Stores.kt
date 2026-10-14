package com.wuhuan.edgepro.data

import android.content.Context
import com.wuhuan.edgepro.core.LogEntry
import com.wuhuan.edgepro.core.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    classDiscriminator = "type"
}

/** 以 JSON 文件持久化的列表，内存中以 StateFlow 暴露。写入先落临时文件再替换。 */
open class JsonListStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
) {
    private val listSerializer = ListSerializer(serializer)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun load(): List<T> = runCatching {
        if (file.exists()) AppJson.decodeFromString(listSerializer, file.readText()) else emptyList()
    }.getOrElse { emptyList() }

    @Synchronized
    fun update(transform: (List<T>) -> List<T>) {
        val next = transform(_items.value)
        _items.value = next
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(listSerializer, next))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** 从其他进程/外部修改后重新读取。 */
    fun reload() {
        _items.value = load()
    }
}

class TaskStore(context: Context) : JsonListStore<Task>(File(context.filesDir, "tasks.json"), Task.serializer()) {

    fun get(id: String): Task? = items.value.firstOrNull { it.id == id }

    fun upsert(task: Task) = update { list ->
        if (list.any { it.id == task.id }) list.map { if (it.id == task.id) task else it } else list + task
    }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun markRun(id: String, time: Long, success: Boolean) = update { list ->
        list.map { if (it.id == id) it.copy(lastRunAt = time, lastSuccess = success) else it }
    }

    fun exportJson(): String = AppJson.encodeToString(ListSerializer(Task.serializer()), items.value)

    /** 导入任务，同 id 覆盖。返回导入数量。 */
    fun importJson(text: String): Int {
        val imported = AppJson.decodeFromString(ListSerializer(Task.serializer()), text)
        update { list ->
            val ids = imported.map { it.id }.toSet()
            list.filterNot { it.id in ids } + imported
        }
        return imported.size
    }
}

class LogStore(context: Context) : JsonListStore<LogEntry>(File(context.filesDir, "logs.json"), LogEntry.serializer()) {

    fun add(entry: LogEntry) = update { list -> (listOf(entry) + list).take(MAX_LOGS) }

    fun clear() = update { emptyList() }

    companion object {
        const val MAX_LOGS = 300
    }
}
