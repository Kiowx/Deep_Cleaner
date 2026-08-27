package com.kiowx.deepcleaner.core

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.PriorityQueue
import kotlin.system.measureTimeMillis

class ExpansionScanner(private val context: Context) {
    private val roots get() = StorageAccess.roots(context)
    private val whitelist = WhitelistRepository(context)
    private val policy get() = SafePathPolicy(roots, whitelist.pathEntries())

    suspend fun scanCustomRules(
        rules: List<CustomCleanRule>,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanReport {
        val activeRules = rules.filter(CustomCleanRule::enabled)
        if (activeRules.isEmpty()) return emptyReport()
        return scanFiles(onProgress) { file ->
            val rule = activeRules.firstOrNull { CustomRuleMatcher.matches(it, file, System.currentTimeMillis()) } ?: return@scanFiles null
            file.toItem(
                CleanCategory.CUSTOM_RULE,
                "规则“${rule.name}”：路径、类型、大小和时间条件均匹配",
                rule.safeByDefault,
            )
        }
    }

    suspend fun scanResiduals(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(0).mapTo(hashSetOf()) { it.packageName.lowercase(Locale.ROOT) }
        }.getOrDefault(hashSetOf())
        val packagePattern = Regex("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z0-9_-]+)+")
        val publicParents = roots.flatMap { root ->
            listOf(root, File(root, "Download"), File(root, "Documents"), File(root, "Pictures"), File(root, "Movies"), File(root, "DCIM"))
        }.filter(File::isDirectory)
        val candidates = publicParents.flatMap { parent -> parent.listFiles().orEmpty().asList() }
            .filter { it.isDirectory && policy.canScan(it) && packagePattern.matches(it.name) }
            .filter { it.name.lowercase(Locale.ROOT) !in installed }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
        val items = mutableListOf<CleanItem>()
        var files = 0L
        var bytes = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            candidates.forEach { directory ->
                currentCoroutineContext().ensureActive()
                runCatching { directoryStats(directory) }
                    .onSuccess { stats ->
                        files += stats.second
                        bytes += stats.first
                        items += directory.toItem(
                            CleanCategory.APP_RESIDUAL,
                            "目录名像应用包名，但设备中未找到对应应用；可能是卸载残留，请确认内容",
                            false,
                            sizeOverride = stats.first,
                        )
                        onProgress(ScanProgress(directory.absolutePath, files, items.size, bytes))
                    }
                    .onFailure { errors++ }
            }
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), files, bytes, 0, errors, elapsed)
    }

    suspend fun scanScreenMedia(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport = scanFiles(onProgress) { file ->
        val normalized = file.absolutePath.replace('\\', '/').lowercase(Locale.ROOT)
        val isImage = file.extension.lowercase(Locale.ROOT) in AdvancedScanner.IMAGE_EXTENSIONS
        val isVideo = file.extension.lowercase(Locale.ROOT) in AdvancedScanner.VIDEO_EXTENSIONS
        val screenshot = isImage && SCREENSHOT_MARKERS.any(normalized::contains)
        val recording = isVideo && RECORDING_MARKERS.any(normalized::contains)
        if (!screenshot && !recording) return@scanFiles null
        val month = java.text.SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(java.util.Date(file.lastModified()))
        file.toItem(
            CleanCategory.MEDIA_COLLECTION,
            "${if (screenshot) "截图" else "录屏"} · $month · 可预览后批量整理",
            false,
        )
    }

    suspend fun scanDuplicateVideos(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0L
        var bytes = 0L
        var errors = 0L
        var foundBytes = 0L
        val items = mutableListOf<CleanItem>()
        val elapsed = measureTimeMillis {
            walk { file ->
                if (file.extension.lowercase(Locale.ROOT) !in AdvancedScanner.VIDEO_EXTENSIONS || file.length() < 512 * 1024) return@walk
                scanned++
                bytes += file.length().coerceAtLeast(0)
                bySize.getOrPut(file.length()) { mutableListOf() } += file
                if (scanned % 50L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, items.size, foundBytes))
            }
            val bySignature = mutableMapOf<String, MutableList<Pair<File, VideoInfo>>>()
            bySize.values.filter { it.size > 1 }.flatten().forEach { file ->
                currentCoroutineContext().ensureActive()
                runCatching {
                    val info = videoInfo(file)
                    val signature = "${file.length()}:${info.durationMs}:${info.width}x${info.height}:${quickDigest(file)}"
                    bySignature.getOrPut(signature) { mutableListOf() } += file to info
                }.onFailure { errors++ }
            }
            bySignature.values.filter { it.size > 1 }.forEachIndexed { groupIndex, group ->
                val byDigest = group.mapNotNull { pair ->
                    runCatching { contentDigest(pair.first) to pair }.onFailure { errors++ }.getOrNull()
                }.groupBy({ it.first }, { it.second })
                byDigest.filterValues { it.size > 1 }.forEach { (digest, exact) ->
                    val keep = exact.maxWith(compareBy<Pair<File, VideoInfo>> { it.first.lastModified() }.thenBy { it.first.name.length })
                    exact.filterNot { it.first == keep.first }.forEach { (file, info) ->
                        val item = file.toItem(
                            CleanCategory.VIDEO_DUPLICATE,
                            "重复视频组 ${groupIndex + 1} · ${formatDuration(info.durationMs)} · ${info.width}×${info.height} · 保留 ${keep.first.name}",
                            false,
                            group = "video-${digest.take(12)}",
                            reference = keep.first.absolutePath,
                        )
                        items += item
                        foundBytes += item.size
                    }
                }
            }
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), scanned, bytes, 0, errors, elapsed)
    }

    suspend fun scanTimeline(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val newest = PriorityQueue<CleanItem>(compareBy(CleanItem::modifiedAt))
        val oldestLarge = PriorityQueue<CleanItem>(compareByDescending(CleanItem::modifiedAt))
        var scanned = 0L
        var bytes = 0L
        var errors = 0L
        val now = System.currentTimeMillis()
        val elapsed = measureTimeMillis {
            walk { file ->
                scanned++
                val size = file.length().coerceAtLeast(0)
                bytes += size
                if (size >= 1024 * 1024) {
                    val ageDays = ((now - file.lastModified()).coerceAtLeast(0) / 86_400_000L).toInt()
                    val period = when {
                        ageDays == 0 -> "今天新增"
                        ageDays <= 7 -> "本周新增"
                        ageDays <= 31 -> "本月新增"
                        else -> "$ageDays 天未修改"
                    }
                    val item = file.toItem(CleanCategory.FILE_TIMELINE, "$period · ${file.extension.ifBlank { "无扩展名" }.uppercase(Locale.ROOT)}", false)
                    newest += item
                    if (newest.size > 700) newest.poll()
                    if (size >= 20L * 1024 * 1024 && ageDays >= 180) {
                        oldestLarge += item
                        if (oldestLarge.size > 300) oldestLarge.poll()
                    }
                }
                if (scanned % 250L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, newest.size + oldestLarge.size, bytes))
            }
        }
        val items = (newest + oldestLarge).distinctBy(CleanItem::id).sortedByDescending(CleanItem::modifiedAt)
        return ScanReport(items, scanned, bytes, 0, errors, elapsed)
    }

    suspend fun scanArchives(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val archives = mutableListOf<File>()
        var scanned = 0L
        var bytes = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            walk { file ->
                scanned++
                bytes += file.length().coerceAtLeast(0)
                if (file.extension.lowercase(Locale.ROOT) in ARCHIVE_EXTENSIONS) archives += file
                if (scanned % 250L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, archives.size, archives.sumOf(File::length)))
            }
        }
        val duplicateKeys = archives.groupBy { it.length() }.filterValues { it.size > 1 }.values
            .flatMap { group -> group.groupBy(::quickDigest).values.filter { it.size > 1 }.flatMap { it.drop(1) } }
            .mapTo(hashSetOf()) { it.absolutePath }
        val cutoff = System.currentTimeMillis() - 180L * 86_400_000L
        val items = archives.map { file ->
            val extracted = File(file.parentFile, file.nameWithoutExtension).let { it.isDirectory && !it.list().isNullOrEmpty() }
            val reason = when {
                file.absolutePath in duplicateKeys -> "内容摘要重复，建议保留一份"
                extracted -> "发现同名非空目录，压缩包可能已经解压"
                file.lastModified() < cutoff -> "超过 180 天未修改的压缩包"
                else -> "压缩包检查项 · 删除前请确认不再需要归档内容"
            }
            file.toItem(CleanCategory.ARCHIVE_CANDIDATE, reason, false)
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), scanned, bytes, 0, errors, elapsed)
    }

    private suspend fun scanFiles(
        onProgress: suspend (ScanProgress) -> Unit,
        classifier: (File) -> CleanItem?,
    ): ScanReport {
        val items = mutableListOf<CleanItem>()
        var scanned = 0L
        var bytes = 0L
        var foundBytes = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            walk { file ->
                scanned++
                bytes += file.length().coerceAtLeast(0)
                runCatching { classifier(file) }.onSuccess { item ->
                    if (item != null) {
                        items += item
                        foundBytes += item.size
                    }
                }.onFailure { errors++ }
                if (scanned % 250L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, items.size, foundBytes))
            }
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), scanned, bytes, 0, errors, elapsed)
    }

    private suspend fun walk(onFile: suspend (File) -> Unit) {
        val stack = ArrayDeque<File>().apply { roots.filter(policy::canScan).forEach(::add) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val current = stack.removeLast()
            val children = runCatching { current.listFiles().orEmpty() }.getOrDefault(emptyArray())
            children.forEach { child ->
                currentCoroutineContext().ensureActive()
                when {
                    Files.isSymbolicLink(child.toPath()) -> Unit
                    child.isDirectory && policy.canScan(child) -> stack.add(child)
                    child.isFile && policy.canScan(child) && !whitelist.isProtected(child) -> onFile(child)
                }
            }
        }
    }

    private fun directoryStats(root: File): Pair<Long, Long> {
        var bytes = 0L
        var files = 0L
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val children = stack.removeLast().listFiles().orEmpty()
            children.forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) return@forEach
                if (child.isDirectory) stack.add(child) else if (child.isFile) {
                    files++
                    bytes += child.length().coerceAtLeast(0)
                }
            }
        }
        return bytes to files
    }

    private fun videoInfo(file: File): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoInfo(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            )
        } finally {
            retriever.release()
        }
    }

    private fun quickDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        file.inputStream().buffered().use { input ->
            val count = input.read(buffer)
            if (count > 0) digest.update(buffer, 0, count)
        }
        if (file.length() > buffer.size) RandomAccessFile(file, "r").use { input ->
            input.seek((file.length() - buffer.size).coerceAtLeast(0))
            val count = input.read(buffer)
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun contentDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun File.toItem(
        category: CleanCategory,
        reason: String,
        selected: Boolean,
        group: String? = null,
        reference: String? = null,
        sizeOverride: Long? = null,
    ) = CleanItem(
        id = runCatching { canonicalPath }.getOrDefault(absolutePath),
        path = absolutePath,
        name = name.ifBlank { absolutePath },
        size = sizeOverride ?: length().coerceAtLeast(0),
        modifiedAt = lastModified(),
        category = category,
        reason = reason,
        selected = selected,
        duplicateGroup = group,
        duplicateReference = reference,
    )

    private data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

    companion object {
        private val SCREENSHOT_MARKERS = listOf("screenshot", "screenshots", "截屏", "截图")
        private val RECORDING_MARKERS = listOf("screenrecord", "screen_record", "screen recorder", "录屏", "屏幕录制")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")

        fun mergeReports(vararg reports: ScanReport): ScanReport = ScanReport(
            items = reports.flatMap(ScanReport::items).distinctBy(CleanItem::id).sortedByDescending(CleanItem::size),
            scannedFiles = reports.sumOf(ScanReport::scannedFiles),
            scannedBytes = reports.sumOf(ScanReport::scannedBytes),
            skipped = reports.sumOf(ScanReport::skipped),
            errors = reports.sumOf(ScanReport::errors),
            elapsedMs = reports.sumOf(ScanReport::elapsedMs),
        )

        private fun emptyReport() = ScanReport(emptyList(), 0, 0, 0, 0, 0)
        private fun formatDuration(milliseconds: Long): String {
            val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
            return "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
        }
    }
}

object CustomRuleMatcher {
    fun matches(rule: CustomCleanRule, file: File, now: Long = System.currentTimeMillis()): Boolean {
        if (rule.pathContains.isNotBlank() && !file.absolutePath.contains(rule.pathContains, ignoreCase = true)) return false
        if (rule.extensions.isNotEmpty() && file.extension.lowercase(Locale.ROOT) !in rule.extensions) return false
        if (file.length() < rule.minimumBytes) return false
        if (rule.olderThanDays > 0 && file.lastModified() > now - rule.olderThanDays * 86_400_000L) return false
        return true
    }
}
