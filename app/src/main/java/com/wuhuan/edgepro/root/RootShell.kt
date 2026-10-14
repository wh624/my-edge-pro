package com.wuhuan.edgepro.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 每次调用启动一个独立的 `su` 进程执行脚本。 */
object RootShell {

    data class Result(val code: Int, val out: String, val err: String) {
        val ok: Boolean get() = code == 0
        val combined: String get() = listOf(out.trim(), err.trim()).filter { it.isNotEmpty() }.joinToString("\n")
    }

    const val CODE_TIMEOUT = -1
    const val CODE_NO_ROOT = -2
    private const val MAX_OUTPUT_CHARS = 256 * 1024

    @Volatile
    var lastRootCheck: Boolean? = null
        private set

    suspend fun exec(script: String, timeoutSec: Long = 60): Result = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder("su").start()
        } catch (e: Exception) {
            lastRootCheck = false
            return@withContext Result(CODE_NO_ROOT, "", "无法执行 su：${e.message}")
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val outReader = drain(process.inputStream, out)
        val errReader = drain(process.errorStream, err)
        try {
            process.outputStream.bufferedWriter().use { w ->
                w.write(script)
                w.write("\nexit \$?\n")
            }
        } catch (_: Exception) {
            // su 被拒绝时 stdin 会立即关闭
        }
        val finished = process.waitFor(timeoutSec.coerceAtLeast(1), TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        outReader.join(2000)
        errReader.join(2000)
        val code = if (finished) process.exitValue() else CODE_TIMEOUT
        val errText = if (finished) err.toString() else err.append("\n执行超时（${timeoutSec}s）").toString()
        Result(code, out.toString(), errText)
    }

    suspend fun checkRoot(): Boolean {
        val r = exec("id", timeoutSec = 30)
        return (r.ok && r.out.contains("uid=0")).also { lastRootCheck = it }
    }

    private fun drain(stream: InputStream, sink: StringBuilder) = thread(isDaemon = true) {
        runCatching {
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    synchronized(sink) {
                        if (sink.length < MAX_OUTPUT_CHARS) sink.appendLine(line)
                    }
                }
            }
        }
    }
}
