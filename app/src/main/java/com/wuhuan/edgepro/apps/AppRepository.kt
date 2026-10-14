package com.wuhuan.edgepro.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.wuhuan.edgepro.core.ShellParsers
import com.wuhuan.edgepro.core.UserSpace
import com.wuhuan.edgepro.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val userId: Int,
    val isSystem: Boolean,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(300)
    private val labelCache = HashMap<String, String>()

    suspend fun loadUsers(): List<UserSpace> {
        val r = RootShell.exec("pm list users", timeoutSec = 30)
        val users = if (r.ok) ShellParsers.parseUsers(r.out) else emptyList()
        return users.ifEmpty { listOf(UserSpace(0, "主空间", true)) }
    }

    /** 读取某个用户空间中的应用。有 root 时通过 pm 读取（支持分身空间），否则只能读取主空间。 */
    suspend fun loadApps(userId: Int): List<InstalledApp> = withContext(Dispatchers.IO) {
        val r = RootShell.exec(
            "pm list packages -3 --user $userId; echo '@@SECTION:sys@@'; pm list packages -s --user $userId",
            timeoutSec = 60,
        )
        val apps = if (r.ok) {
            val third = ShellParsers.parsePackages(r.out.substringBefore("@@SECTION:sys@@"))
            val system = ShellParsers.parsePackages(r.out.substringAfter("@@SECTION:sys@@", ""))
            third.map { InstalledApp(it, labelOf(it), userId, false) } +
                (system - third).map { InstalledApp(it, labelOf(it), userId, true) }
        } else if (userId == 0) {
            installedInfos().map {
                InstalledApp(it.packageName, labelOf(it.packageName), 0, it.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            }
        } else {
            emptyList()
        }
        apps.sortedWith(compareBy<InstalledApp> { it.isSystem }.thenBy { it.label.lowercase() })
    }

    fun hasLaunchEntry(pkg: String): Boolean = pm.getLaunchIntentForPackage(pkg) != null

    fun labelOf(pkg: String): String = synchronized(labelCache) {
        labelCache.getOrPut(pkg) {
            appInfo(pkg)?.loadLabel(pm)?.toString() ?: pkg
        }
    }

    suspend fun iconOf(pkg: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache.get(pkg) ?: runCatching {
            appInfo(pkg)?.loadIcon(pm)?.toBitmap(sizePx)?.asImageBitmap()
        }.getOrNull()?.also { iconCache.put(pkg, it) }
    }

    @Suppress("DEPRECATION")
    private fun installedInfos(): List<ApplicationInfo> =
        pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)

    /** MATCH_UNINSTALLED_PACKAGES 能读到只安装在其他用户空间里的应用信息。 */
    @Suppress("DEPRECATION")
    private fun appInfo(pkg: String): ApplicationInfo? =
        runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES) }.getOrNull()

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return Bitmap.createScaledBitmap(bitmap, size, size, true)
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }
}
