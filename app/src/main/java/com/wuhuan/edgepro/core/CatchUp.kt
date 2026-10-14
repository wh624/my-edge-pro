package com.wuhuan.edgepro.core

/** 判定 App 进程不在期间错过的触发。 */
object CatchUp {

    /**
     * [expected] 为每个任务上一次注册的触发时间。返回需要补执行的 (任务, 原定触发时间)：
     * 任务启用、原定时间已过且在 [graceMs] 之内、并且之后没有执行过。
     */
    fun due(tasks: List<Task>, expected: Map<String, Long>, now: Long, graceMs: Long): List<Pair<Task, Long>> {
        if (graceMs <= 0) return emptyList()
        return tasks.mapNotNull { task ->
            val at = expected[task.id] ?: return@mapNotNull null
            val missed = task.enabled && task.steps.isNotEmpty() &&
                at <= now && now - at <= graceMs &&
                (task.lastRunAt ?: 0L) < at
            if (missed) task to at else null
        }
    }

    /** 同一次触发被闹钟和补执行重复投递时，用于跳过后到的那次。 */
    fun alreadyRan(task: Task, scheduledAt: Long): Boolean = (task.lastRunAt ?: 0L) >= scheduledAt
}
