package com.wuhuan.edgepro.core

object DirectBoot {
    /** 含「启动应用」的任务在用户未解锁时无法真正拉起其他应用，需要整段延后。 */
    fun deferLaunch(steps: List<Step>, userUnlocked: Boolean): Boolean =
        !userUnlocked && steps.any { it is Step.Launch }
}
