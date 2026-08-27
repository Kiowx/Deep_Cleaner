package com.kiowx.deepcleaner.core

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Locale
import java.util.zip.ZipFile
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.measureTimeMillis

class AdvancedScanner(private val context: Context) {
    private val roots get() = StorageAccess.roots(context)
    private val whitelist = WhitelistRepository(context)
    private val policy get() = SafePathPolicy(roots, whitelist.pathEntries())

    suspend fun analyzeStorage(onProgress: suspend (ScanProgress) -> Unit = {}): StorageAnalysis {
        val categoryBytes = mutableMapOf<StorageCategory, Long>()
        val categoryFiles = mutableMapOf<StorageCategory, Long>()
        val directoryBytes = mutableMapOf<String, Long>()
        val directoryFiles = mutableMapOf<String, Long>()
        var files = 0L
        var bytes = 0L
        walk { file ->
            files++
            val size = file.length().coerceAtLeast(0)
            bytes += size
            val category = storageCategory(file)
            categoryBytes[category] = categoryBytes.getOrDefault(category, 0) + size
            categoryFiles[category] = categoryFiles.getOrDefault(category, 0) + 1
            val directory = displayDirectory(file)
            directoryBytes[directory] = directoryBytes.getOrDefault(directory, 0) + size
            directoryFiles[directory] = directoryFiles.getOrDefault(directory, 0) + 1
            if (files % 200L == 0L) onProgress(ScanProgress(file.absolutePath, files, categoryBytes.size, bytes))
        }
        return StorageAnalysis(
            buckets = StorageCategory.entries.map { StorageBucket(it, categoryBytes.getOrDefault(it, 0), categoryFiles.getOrDefault(it, 0)) }
                .filter { it.files > 0 }.sortedByDescending(StorageBucket::bytes),
            directories = directoryBytes.map { (path, size) -> DirectoryUsage(path, size, directoryFiles.getOrDefault(path, 0)) }
                .sortedByDescending(DirectoryUsage::bytes).take(30),
            scannedFiles = files,
            scannedBytes = bytes,
        )
    }

    suspend fun scanSimilarMedia(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val fingerprints = mutableListOf<MediaFingerprint>()
        var scanned = 0L
        var bytes = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            walk { file ->
                if (file.extension.lowercase(Locale.ROOT) !in IMAGE_EXTENSIONS || file.length() < 64 * 1024) return@walk
                scanned++
                bytes += file.length().coerceAtLeast(0)
                runCatching { fingerprint(file) }.onSuccess { it?.let(fingerprints::add) }.onFailure { errors++ }
                if (scanned % 25L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, 0, bytes))
            }
        }
        val parent = IntArray(fingerprints.size) { it }
        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun union(left: Int, right: Int) {
            val a = root(left)
            val b = root(right)
            if (a != b) parent[b] = a
        }
        val bands = mutableMapOf<String, MutableList<Int>>()
        fingerprints.forEachIndexed { index, item ->
            for (band in 0 until 4) {
                val key = "$band:${(item.hash ushr (band * 16)) and 0xffff}:${(item.width.toDouble() / item.height * 10).toInt()}"
                bands[key].orEmpty().forEach { candidate ->
                    val other = fingerprints[candidate]
                    val aspectDelta = abs(item.width.toDouble() / item.height - other.width.toDouble() / other.height)
                    if (
                        aspectDelta < .05 &&
                        SimilarityMetrics.hammingDistance(item.hash, other.hash) <= 6 &&
                        SimilarityMetrics.meanAbsoluteDifference(item.luma, other.luma) <= 18.0
                    ) union(index, candidate)
                }
                bands.getOrPut(key) { mutableListOf() } += index
            }
        }
        val groups = fingerprints.indices.groupBy(::root).values.filter { it.size > 1 }
        val items = mutableListOf<CleanItem>()
        val groupedPaths = mutableSetOf<String>()
        groups.forEachIndexed { groupIndex, indices ->
            val members = indices.map(fingerprints::get)
            groupedPaths += members.map { it.file.absolutePath }
            val keep = members.maxWithOrNull(compareBy<MediaFingerprint> { it.sharpness }.thenBy { it.file.length() }) ?: return@forEachIndexed
            members.filterNot { it.file == keep.file }.forEach { media ->
                val screenshot = media.file.absolutePath.contains("screenshot", true) || media.file.name.contains("截屏") || media.file.name.contains("截图")
                val blur = media.sharpness < keep.sharpness * .55
                val reason = when {
                    blur -> "相似组 ${groupIndex + 1} · 清晰度较低，建议保留 ${keep.file.name}"
                    screenshot -> "相似组 ${groupIndex + 1} · 重复截图，建议保留 ${keep.file.name}"
                    else -> "相似组 ${groupIndex + 1} · 建议保留更清晰的 ${keep.file.name}"
                }
                items += media.file.toCleanItem(CleanCategory.SIMILAR_MEDIA, reason, selected = false, group = "similar-$groupIndex", reference = keep.file.absolutePath)
            }
        }
        fingerprints.asSequence().filterNot { it.file.absolutePath in groupedPaths }.forEach { media ->
            val normalized = media.file.name.lowercase(Locale.ROOT)
            val reason = when {
                media.averageLuma < 30 -> "画面整体过暗，建议预览确认"
                media.sharpness < 320 -> "清晰度较低，可能是模糊照片"
                normalized.contains("burst") || normalized.contains("连拍") -> "检测到连拍命名，可与同组照片比较后整理"
                else -> return@forEach
            }
            items += media.file.toCleanItem(CleanCategory.SIMILAR_MEDIA, reason, selected = false)
        }
        return ScanReport(items.distinctBy(CleanItem::id).sortedByDescending(CleanItem::size), scanned, bytes, 0, errors, elapsed)
    }

    suspend fun scanMediaForOptimization(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport = scanByPredicate(
        category = CleanCategory.MEDIA_OPTIMIZE,
        reason = { file -> if (file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS) "大视频，可转为 720p H.264 副本" else "大图片，可生成高质量压缩副本" },
        predicate = { file ->
            val ext = file.extension.lowercase(Locale.ROOT)
            (ext in IMAGE_EXTENSIONS && file.length() >= 3L * 1024 * 1024) ||
                (ext in VIDEO_EXTENSIONS && file.length() >= 50L * 1024 * 1024)
        },
        onProgress = onProgress,
    )

    suspend fun scanPrivacyRisks(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val sensitivePattern = Regex("(?i)(password|passwd|token|secret|api[_-]?key|authorization)\\s*[:=]")
        return scanByPredicate(
            category = CleanCategory.PRIVACY_RISK,
            reason = { file ->
                val name = file.name.lowercase(Locale.ROOT)
                when {
                    file.extension.lowercase(Locale.ROOT) in setOf("log", "trace", "dmp", "stacktrace") -> "日志或崩溃记录可能包含设备信息"
                    file.extension.lowercase(Locale.ROOT) in setOf("db", "sqlite", "sqlite3") -> "导出的数据库文件"
                    name.contains("backup") || name.contains("export") -> "备份或导出文件"
                    else -> "检测到疑似凭据字段"
                }
            },
            predicate = { file ->
                val extension = file.extension.lowercase(Locale.ROOT)
                val name = file.name.lowercase(Locale.ROOT)
                val obvious = extension in setOf("log", "trace", "dmp", "stacktrace", "db", "sqlite", "sqlite3") ||
                    name.contains("backup") || name.contains("export")
                obvious || (extension in setOf("txt", "json", "xml", "csv", "env", "ini", "conf") && file.length() in 1..2_097_152 &&
                    runCatching { file.inputStream().bufferedReader().use { it.readText().take(262_144) }.contains(sensitivePattern) }.getOrDefault(false))
            },
            onProgress = onProgress,
        )
    }

    suspend fun scanApkArchives(onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val pm = context.packageManager
        val entries = mutableListOf<Pair<File, ApkInfo>>()
        var scanned = 0L
        var bytes = 0L
        var errors = 0L
        val elapsed = measureTimeMillis {
            walk { file ->
                if (file.extension.lowercase(Locale.ROOT) != "apk") return@walk
                scanned++
                bytes += file.length().coerceAtLeast(0)
                val archive = runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
                    }
                }.getOrNull()
                if (archive == null) errors++ else {
                    val installed = runCatching {
                        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(archive.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                        else @Suppress("DEPRECATION") pm.getPackageInfo(
                            archive.packageName,
                            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
                        )
                    }.getOrNull()
                    entries += file to ApkInfo(
                        archive.packageName,
                        archive.versionName.orEmpty(),
                        versionCode(archive),
                        installed?.let(::versionCode),
                        apkArchitectures(file),
                        signingDigest(archive),
                        installed?.let(::signingDigest),
                    )
                }
                if (scanned % 10L == 0L) onProgress(ScanProgress(file.absolutePath, scanned, entries.size, bytes))
            }
        }
        val newest = entries.groupBy { it.second.packageName }.mapValues { (_, values) -> values.maxOf { it.second.versionCode } }
        val items = entries.map { (file, info) ->
            val status = when {
                info.installedVersion != null && info.versionCode <= info.installedVersion -> "${info.packageName} ${info.versionName} · 已安装同版或更新版本"
                info.versionCode < newest.getValue(info.packageName) -> "${info.packageName} ${info.versionName} · 同包名存在更新安装包"
                info.installedVersion == null -> "${info.packageName} ${info.versionName} · 尚未安装"
                else -> "${info.packageName} ${info.versionName} · 可用于升级"
            }
            val signature = when {
                info.installedSignature == null -> "签名 ${info.signature}"
                info.signature == info.installedSignature -> "签名一致 ${info.signature}"
                else -> "签名不一致 ${info.signature}"
            }
            val reason = "$status · ${info.architectures} · $signature"
            file.toCleanItem(CleanCategory.APK_ARCHIVE, reason, selected = false)
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), scanned, bytes, 0, errors, elapsed)
    }

    suspend fun archiveDownloads(items: List<CleanItem>): Int {
        val archiveRoot = File(roots.firstOrNull() ?: return 0, "Download/DeepCleanerArchive/${java.time.LocalDate.now()}")
        if (!archiveRoot.mkdirs() && !archiveRoot.isDirectory) return 0
        var moved = 0
        items.forEach { item ->
            currentCoroutineContext().ensureActive()
            val source = item.file
            if (!source.isFile || !policy.canDelete(source)) return@forEach
            var target = File(archiveRoot, source.name)
            var suffix = 1
            while (target.exists()) target = File(archiveRoot, "${source.nameWithoutExtension}-$suffix.${source.extension}".trimEnd('.')) .also { suffix++ }
            val ok = runCatching { source.renameTo(target) || (source.copyTo(target).let { source.delete() }) }.getOrDefault(false)
            if (ok) moved++
        }
        return moved
    }

    private suspend fun scanByPredicate(
        category: CleanCategory,
        reason: (File) -> String,
        predicate: (File) -> Boolean,
        onProgress: suspend (ScanProgress) -> Unit,
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
                runCatching {
                    if (predicate(file)) {
                        val item = file.toCleanItem(category, reason(file), false)
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

    private fun displayDirectory(file: File): String {
        val root = roots.firstOrNull { file.absolutePath.startsWith(it.absolutePath) } ?: return file.parent.orEmpty()
        val relative = file.relativeToOrNull(root)?.invariantSeparatorsPath.orEmpty()
        val segments = relative.split('/').dropLast(1)
        return if (segments.isEmpty()) root.name else segments.take(2).joinToString("/")
    }

    private fun storageCategory(file: File): StorageCategory = StorageClassifier.categoryForExtension(file.extension)

    private fun fingerprint(file: File): MediaFingerprint? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val tiny = android.graphics.Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        var sharpness = 0.0
        val luma = ByteArray(64)
        for (y in 0 until 8) for (x in 0 until 8) {
            val left = luminance(tiny.getPixel(x, y))
            val right = luminance(tiny.getPixel(x + 1, y))
            if (left > right) hash = hash or (1L shl bit)
            sharpness += abs(left - right)
            if (y > 0) sharpness += abs(left - luminance(tiny.getPixel(x, y - 1)))
            luma[bit] = left.toByte()
            bit++
        }
        tiny.recycle()
        if (bitmap !== tiny) bitmap.recycle()
        val averageLuma = luma.sumOf { it.toInt() and 0xff }.toDouble() / luma.size
        return MediaFingerprint(file, hash, sharpness, bounds.outWidth, bounds.outHeight, luma, averageLuma)
    }

    private fun luminance(pixel: Int): Int = ((android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000)

    private fun apkArchitectures(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val architectures = zip.entries().asSequence().mapNotNull { entry ->
                entry.name.takeIf { it.startsWith("lib/") }?.split('/')?.getOrNull(1)
            }.distinct().toList()
            if (architectures.isEmpty()) "通用" else architectures.joinToString("/")
        }
    }.getOrDefault("架构未知")

    private fun signingDigest(info: android.content.pm.PackageInfo): String = runCatching {
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty()
        else @Suppress("DEPRECATION") info.signatures.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(certificates.first().toByteArray())
        digest.take(6).joinToString("") { "%02X".format(Locale.ROOT, it) }
    }.getOrDefault("未知")

    @Suppress("DEPRECATION")
    private fun versionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun File.toCleanItem(
        category: CleanCategory,
        reason: String,
        selected: Boolean,
        group: String? = null,
        reference: String? = null,
    ) = CleanItem(
        id = runCatching { canonicalPath }.getOrDefault(absolutePath), path = absolutePath, name = name,
        size = length().coerceAtLeast(0), modifiedAt = lastModified(), category = category, reason = reason,
        selected = selected, duplicateGroup = group, duplicateReference = reference,
    )

    private data class MediaFingerprint(
        val file: File,
        val hash: Long,
        val sharpness: Double,
        val width: Int,
        val height: Int,
        val luma: ByteArray,
        val averageLuma: Double,
    )
    private data class ApkInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val installedVersion: Long?,
        val architectures: String,
        val signature: String,
        val installedSignature: String?,
    )

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "3gp")
    }
}

object SimilarityMetrics {
    fun hammingDistance(left: Long, right: Long): Int = java.lang.Long.bitCount(left xor right)

    fun meanAbsoluteDifference(left: ByteArray, right: ByteArray): Double {
        if (left.isEmpty() || left.size != right.size) return Double.POSITIVE_INFINITY
        return left.indices.sumOf { abs((left[it].toInt() and 0xff) - (right[it].toInt() and 0xff)) }.toDouble() / left.size
    }
}

object StorageClassifier {
    fun categoryForExtension(rawExtension: String): StorageCategory = when (rawExtension.lowercase(Locale.ROOT).removePrefix(".")) {
        in AdvancedScanner.IMAGE_EXTENSIONS -> StorageCategory.IMAGES
        in AdvancedScanner.VIDEO_EXTENSIONS -> StorageCategory.VIDEOS
        in setOf("mp3", "aac", "wav", "flac", "m4a", "ogg", "opus") -> StorageCategory.AUDIO
        in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub") -> StorageCategory.DOCUMENTS
        in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> StorageCategory.ARCHIVES
        in setOf("apk", "apks", "xapk", "apkm") -> StorageCategory.INSTALLERS
        else -> StorageCategory.OTHER
    }
}
