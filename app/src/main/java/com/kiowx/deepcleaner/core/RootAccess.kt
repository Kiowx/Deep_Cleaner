package com.kiowx.deepcleaner.core

import android.content.Context
import java.util.Locale
import java.util.concurrent.TimeUnit

object RootAccess {
    private val allowedCachePath = Regex("^/data/(?:user/\\d+|data)/[A-Za-z0-9._-]+/cache/?$")

    fun isAllowedCachePath(path: String): Boolean = allowedCachePath.matches(path.removeSuffix("/"))

    fun isAvailable(): Boolean = runCommand("id", 3).let { it.exitCode == 0 && it.output.contains("uid=0") }

    fun scanCaches(context: Context): ScanReport {
        val started = System.currentTimeMillis()
        val result = runCommand("du -sk /data/user/0/*/cache 2>/dev/null", 15)
        if (result.exitCode != 0 && result.output.isBlank()) error("Root 命令执行失败或未授权")
        val items = result.output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            val kilobytes = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val path = parts.getOrNull(1)?.trim()?.removeSuffix("/") ?: return@mapNotNull null
            if (!isAllowedCachePath(path) || path.contains("/${context.packageName}/")) return@mapNotNull null
            val packageName = path.substringAfterLast("/cache").ifBlank {
                path.substringBeforeLast("/cache").substringAfterLast('/')
            }
            CleanItem(
                id = "root:$path",
                path = path,
                name = packageName,
                size = kilobytes.coerceAtLeast(0) * 1024,
                modifiedAt = 0,
                category = CleanCategory.ROOT_CACHE,
                reason = "Root 检测到 $packageName 的私有缓存；清理后应用可能需要重新加载内容",
                selected = false,
            )
        }.distinctBy(CleanItem::path).sortedByDescending(CleanItem::size).toList()
        return ScanReport(
            items = items,
            scannedFiles = items.size.toLong(),
            scannedBytes = items.sumOf(CleanItem::size),
            skipped = 0,
            errors = if (result.exitCode == 0) 0 else 1,
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    fun clearCache(path: String): Boolean {
        val normalized = path.removeSuffix("/")
        if (!isAllowedCachePath(normalized)) return false
        val quoted = "'${normalized.replace("'", "'\\''")}'"
        return runCommand("find $quoted -mindepth 1 -maxdepth 1 -exec rm -rf {} +", 30).exitCode == 0
    }

    private fun runCommand(command: String, timeoutSeconds: Long): CommandResult = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().take(2_000_000) }
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            CommandResult(-1, "命令超时")
        } else CommandResult(process.exitValue(), output)
    }.getOrElse { CommandResult(-1, it.message.orEmpty()) }

    private data class CommandResult(val exitCode: Int, val output: String)
}
