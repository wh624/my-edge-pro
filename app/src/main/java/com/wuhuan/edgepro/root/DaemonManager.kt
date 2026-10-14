package com.wuhuan.edgepro.root

import com.wuhuan.edgepro.core.DaemonScripts
import com.wuhuan.edgepro.core.DaemonStatus

class DaemonManager(private val packageName: String) {

    /** 安装/更新守护脚本与开机脚本，并确保守护进程在运行。 */
    suspend fun ensure(): DaemonStatus =
        DaemonScripts.parseStatus(RootShell.exec(DaemonScripts.install(packageName), timeoutSec = 30).out)

    suspend fun uninstall(): DaemonStatus =
        DaemonScripts.parseStatus(RootShell.exec(DaemonScripts.uninstall(), timeoutSec = 30).out)

    suspend fun status(): DaemonStatus =
        DaemonScripts.parseStatus(RootShell.exec(DaemonScripts.status(), timeoutSec = 15).out)

    suspend fun diagnose(): String =
        RootShell.exec(DaemonScripts.diagnose(packageName), timeoutSec = 60).combined
}
