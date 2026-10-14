package com.wuhuan.edgepro.data

import android.content.Context
import android.os.UserManager
import java.io.File

/** 把升级前写在凭证加密目录里的任务/设置复制到设备加密目录，解锁前才能读到。 */
object DeviceStorage {

    fun migrate(context: Context) {
        val userManager = context.getSystemService(UserManager::class.java) ?: return
        if (!userManager.isUserUnlocked) return
        val de = File(context.applicationInfo.deviceProtectedDataDir)
        val dataDir = File(context.applicationInfo.dataDir)
        val ce = if (dataDir.canonicalPath != de.canonicalPath) dataDir else File("/data/user/0/${context.packageName}")
        if (ce.canonicalPath == de.canonicalPath) return
        copyMissing(File(ce, "files"), File(de, "files"))
        copyMissing(File(ce, "shared_prefs"), File(de, "shared_prefs"))
    }

    private fun copyMissing(from: File, to: File) {
        if (!from.isDirectory) return
        if (from.canonicalPath == to.canonicalPath) return
        to.mkdirs()
        from.listFiles()?.forEach { src ->
            if (!src.isFile) return@forEach
            val dst = File(to, src.name)
            if (!dst.exists()) src.copyTo(dst)
        }
    }
}
