package com.kiowx.deepcleaner.core

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import kotlin.system.measureTimeMillis

class CleanerEngine(private val context: Context) {
    private val roots get() = StorageAccess.roots(context)
    private val whitelist = WhitelistRepository(context)
    private val policy get() = SafePathPolicy(roots, whitelist.pathEntries())

    suspend fun scanJunk(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val items = mutableListOf<CleanItem>()
        var files = 0L
        var bytes = 0L
        var foundBytes = 0L
        var skipped = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            walkFiles(
                onFile = { file ->
                    files++
                    val size = file.length().coerceAtLeast(0)
                    bytes += size
                    FileClassifier.classify(file)?.let { match ->
                        val item = file.toItem(
                            category = match.category,
                            reason = match.reason,
                            selected = match.safeByDefault,
                        )
                        items += item
                        foundBytes += item.size
                    }
                    if (files % 200L == 0L) {
                        onProgress(ScanProgress(file.absolutePath, files, items.size, foundBytes))
                    }
                },
                onSkipped = { skipped++ },
                onError = { errors++ },
            )
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), files, bytes, skipped, errors, elapsed)
    }

    suspend fun scanLargeFiles(
        minimumBytes: Long,
        limit: Int = 500,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanReport {
        val found = java.util.PriorityQueue<CleanItem>(compareBy(CleanItem::size))
        var files = 0L
        var bytes = 0L
        var skipped = 0L
        var errors = 0L
        var foundBytes = 0L
        val elapsed = measureTimeMillis {
            walkFiles(
                onFile = { file ->
                    files++
                    val size = file.length().coerceAtLeast(0)
                    bytes += size
                    if (size >= minimumBytes) {
                        val item = file.toItem(CleanCategory.LARGE_FILE, "大于 ${formatBytes(minimumBytes)}", false)
                        found += item
                        foundBytes += item.size
                        if (found.size > limit.coerceAtLeast(1)) found.poll()?.let { foundBytes -= it.size }
                    }
                    if (files % 200L == 0L) onProgress(ScanProgress(file.absolutePath, files, found.size, foundBytes))
                },
                onSkipped = { skipped++ },
                onError = { errors++ },
            )
        }
        return ScanReport(found.sortedByDescending(CleanItem::size), files, bytes, skipped, errors, elapsed)
    }

    suspend fun scanDownloads(
        olderThanDays: Int = 30,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanReport {
        val downloadRoots = roots.map { File(it, EnvironmentDirectory.DOWNLOADS) }.filter(File::isDirectory)
        val cutoff = System.currentTimeMillis() - olderThanDays.coerceAtLeast(0) * 86_400_000L
        val found = mutableListOf<CleanItem>()
        var files = 0L
        var bytes = 0L
        var skipped = 0L
        var errors = 0L
        var foundBytes = 0L
        val elapsed = measureTimeMillis {
            walkFiles(
                scanRoots = downloadRoots,
                onFile = { file ->
                    files++
                    bytes += file.length().coerceAtLeast(0)
                    if (file.lastModified() <= cutoff || FileClassifier.isInstaller(file)) {
                        val type = file.extension.ifBlank { "无扩展名" }.uppercase(Locale.ROOT)
                        val location = file.parentFile?.name.orEmpty()
                        val smartType = when (file.extension.lowercase(Locale.ROOT)) {
                            "apk", "apks", "xapk", "apkm" -> "安装包"
                            "zip", "rar", "7z", "tar", "gz" -> "压缩包"
                            "mp4", "mkv", "mov", "avi", "webm" -> "视频"
                            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> "文档"
                            "crdownload", "download", "part" -> "未完成下载"
                            else -> "下载文件"
                        }
                        val reason = if (FileClassifier.isInstaller(file)) "$smartType · $type · $location · 已下载的安装文件" else "$smartType · $type · $location · 超过 $olderThanDays 天未修改"
                        val item = file.toItem(CleanCategory.DOWNLOAD, reason, false)
                        found += item
                        foundBytes += item.size
                    }
                    if (files % 100L == 0L) onProgress(ScanProgress(file.absolutePath, files, found.size, foundBytes))
                },
                onSkipped = { skipped++ },
                onError = { errors++ },
            )
        }
        return ScanReport(found.sortedByDescending(CleanItem::size), files, bytes, skipped, errors, elapsed)
    }

    suspend fun scanDuplicates(
        minimumBytes: Long = 256 * 1024,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanReport {
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var files = 0L
        var bytes = 0L
        var skipped = 0L
        var errors = 0L
        var foundBytes = 0L
        val found = mutableListOf<CleanItem>()
        val elapsed = measureTimeMillis {
            walkFiles(
                onFile = { file ->
                    files++
                    val size = file.length().coerceAtLeast(0)
                    bytes += size
                    if (size >= minimumBytes) bySize.getOrPut(size) { mutableListOf() } += file
                    if (files % 250L == 0L) onProgress(ScanProgress(file.absolutePath, files, found.size, foundBytes))
                },
                onSkipped = { skipped++ },
                onError = { errors++ },
            )
            var checked = 0L
            bySize.values.asSequence().filter { it.size > 1 }.forEach { sameSize ->
                currentCoroutineContext().ensureActive()
                val byQuickDigest = mutableMapOf<String, MutableList<File>>()
                sameSize.forEach { file ->
                    currentCoroutineContext().ensureActive()
                    runCatching { quickDigest(file) }
                        .onSuccess { digest -> byQuickDigest.getOrPut(digest) { mutableListOf() } += file }
                        .onFailure { errors++ }
                    checked++
                    if (checked % 25L == 0L) onProgress(ScanProgress("正在快速比对：${file.name}", files, found.size, foundBytes))
                }
                byQuickDigest.values.asSequence().filter { it.size > 1 }.forEach { quickGroup ->
                    val byDigest = mutableMapOf<String, MutableList<File>>()
                    quickGroup.forEach { file ->
                        currentCoroutineContext().ensureActive()
                        runCatching { stableDigest(file) }
                            .onSuccess { digest -> byDigest.getOrPut(digest) { mutableListOf() } += file }
                            .onFailure { errors++ }
                        checked++
                        if (checked % 10L == 0L) onProgress(ScanProgress("正在完整校验：${file.name}", files, found.size, foundBytes))
                    }
                    byDigest.filterValues { it.size > 1 }.forEach { (digest, group) ->
                        val ordered = group.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.absolutePath.length })
                        ordered.drop(1).forEach { duplicate ->
                            val item = duplicate.toItem(
                                category = CleanCategory.DUPLICATE,
                                reason = "与保留副本 ${ordered.first().name} 内容一致",
                                selected = true,
                                group = digest.take(12),
                                reference = ordered.first().absolutePath,
                            )
                            found += item
                            foundBytes += item.size
                        }
                    }
                }
            }
        }
        return ScanReport(found.sortedByDescending(CleanItem::size), files, bytes, skipped, errors, elapsed)
    }

    suspend fun scanEmptyFolders(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val found = mutableListOf<CleanItem>()
        val directories = mutableListOf<File>()
        var scanned = 0L
        var skipped = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            val stack = ArrayDeque<File>().apply { roots.filter(policy::canScan).forEach(::add) }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = stack.removeLast()
                directories += dir
                val children = runCatching { dir.listFiles().orEmpty() }
                    .onFailure { errors++ }
                    .getOrDefault(emptyArray())
                children.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && policy.canScan(it) }.forEach(stack::add)
                scanned++
                if (scanned % 100L == 0L) onProgress(ScanProgress(dir.absolutePath, scanned, found.size, 0))
            }
            directories.asReversed().forEach { dir ->
                currentCoroutineContext().ensureActive()
                if (dir !in roots && policy.canDelete(dir)) {
                    val empty = runCatching { dir.list()?.isEmpty() == true }.onFailure { errors++ }.getOrDefault(false)
                    if (empty) found += dir.toItem(CleanCategory.EMPTY_FOLDER, "目录中没有文件", false)
                } else skipped++
            }
        }
        return ScanReport(found.sortedBy { it.path.length }, scanned, 0, skipped, errors, elapsed)
    }

    suspend fun deleteItems(
        items: List<CleanItem>,
        mode: DeleteMode,
        trashManager: TrashManager,
        onProgress: suspend (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
    ): DeleteResult {
        var deleted = 0
        var failed = 0
        var released = 0L
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val file = item.file
            if (item.category == CleanCategory.ROOT_CACHE) {
                val ok = RootAccess.clearCache(item.path)
                if (ok) {
                    deleted++
                    released += item.size
                } else failed++
                onProgress(index + 1, items.size, item.name)
                return@forEachIndexed
            }
            val safe = policy.canDelete(file) && verifyCandidate(item)
            if (safe) {
                val ok = when (mode) {
                    DeleteMode.TRASH -> trashManager.moveToTrash(file, item.size)
                    DeleteMode.PERMANENT -> permanentDelete(file)
                }
                if (ok) {
                    deleted++
                    released += item.size
                } else failed++
            } else failed++
            onProgress(index + 1, items.size, item.name)
        }
        return DeleteResult(deleted, failed, released)
    }

    private fun verifyCandidate(item: CleanItem): Boolean {
        val file = item.file
        if (whitelist.isProtected(file)) return false
        if (!file.exists()) return false
        if (item.category == CleanCategory.EMPTY_FOLDER) return file.isDirectory && file.list()?.isEmpty() == true
        if (item.category == CleanCategory.APP_RESIDUAL) return file.isDirectory
        if (!file.isFile || file.length() != item.size) return false
        if ((item.category == CleanCategory.DUPLICATE || item.category == CleanCategory.VIDEO_DUPLICATE) && item.duplicateGroup != null) {
            val reference = item.duplicateReference?.let(::File) ?: return false
            if (!reference.isFile || reference.length() != item.size || reference.absolutePath == file.absolutePath) return false
            return runCatching {
                val expectedPrefix = item.duplicateGroup.removePrefix("video-")
                stableDigest(reference).startsWith(expectedPrefix) && stableDigest(file).startsWith(expectedPrefix)
            }.getOrDefault(false)
        }
        return true
    }

    private fun permanentDelete(file: File): Boolean = runCatching {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }.getOrDefault(false)

    private suspend fun walkFiles(
        scanRoots: List<File> = roots,
        onFile: suspend (File) -> Unit,
        onSkipped: () -> Unit,
        onError: () -> Unit,
    ) {
        val stack = ArrayDeque<File>().apply { scanRoots.filter(policy::canScan).forEach(::add) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val current = stack.removeLast()
            val children = runCatching { current.listFiles().orEmpty() }
                .onFailure { onError() }
                .getOrDefault(emptyArray())
            children.forEach { child ->
                currentCoroutineContext().ensureActive()
                when {
                    Files.isSymbolicLink(child.toPath()) -> onSkipped()
                    child.isDirectory && policy.canScan(child) -> stack.add(child)
                    child.isFile && policy.canScan(child) && !whitelist.isProtected(child) -> onFile(child)
                    else -> onSkipped()
                }
            }
        }
    }

    private fun stableDigest(file: File): String {
        val before = file.length() to file.lastModified()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        check(before == (file.length() to file.lastModified())) { "文件在校验过程中发生变化" }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun quickDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(QUICK_DIGEST_BYTES)
        file.inputStream().buffered().use { input ->
            val count = input.read(buffer)
            if (count > 0) digest.update(buffer, 0, count)
        }
        if (file.length() > QUICK_DIGEST_BYTES) {
            java.io.RandomAccessFile(file, "r").use { input ->
                input.seek((file.length() - QUICK_DIGEST_BYTES).coerceAtLeast(0))
                val count = input.read(buffer)
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun File.toItem(
        category: CleanCategory,
        reason: String,
        selected: Boolean = category.defaultSelected,
        group: String? = null,
        reference: String? = null,
    ) = CleanItem(
        id = runCatching { canonicalPath }.getOrDefault(absolutePath),
        path = absolutePath,
        name = name.ifBlank { absolutePath },
        size = if (isFile) length().coerceAtLeast(0) else 0,
        modifiedAt = lastModified(),
        category = category,
        reason = reason,
        selected = selected,
        duplicateGroup = group,
        duplicateReference = reference,
    )

    private object EnvironmentDirectory { const val DOWNLOADS = "Download" }

    private companion object {
        const val QUICK_DIGEST_BYTES = 64 * 1024
    }
}

data class DeleteResult(val deleted: Int, val failed: Int, val releasedBytes: Long)

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024 && unit < units.lastIndex)
    return if (value >= 100) "%.0f %s".format(Locale.ROOT, value, units[unit])
    else "%.1f %s".format(Locale.ROOT, value, units[unit])
}
