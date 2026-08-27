package com.kiowx.deepcleaner.core

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Locale
import kotlin.system.measureTimeMillis

enum class SocialPlatform { QQ, WECHAT }

data class SocialFileMatch(
    val category: CleanCategory,
    val reason: String,
    val selected: Boolean = category.defaultSelected,
)

object SocialFileClassifier {
    private val cacheSegments = setOf(
        "cache", "cache2", "cachedata", "diskcache", "temp", "tmp", "xlog", "log", "logs",
        "crash", "crashes", "thumb", "thumbs", "thumbnail", "thumbnails", ".thumbs",
    )
    private val temporaryExtensions = setOf("tmp", "temp", "log")
    private val mediaSegments = setOf(
        "image", "image2", "images", "photo", "photos", "video", "videos", "shortvideo",
        "voice", "voice2", "audio", "music", "sns", "emoji", "emoticon", "favorite",
    )
    private val receivedFileSegments = setOf(
        "qqfile_recv", "download", "downloads", "file", "files", "attachment", "attachments",
        "msgattach", "record", "openapi",
    )
    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "mp4", "mkv", "mov", "avi",
        "3gp", "m4a", "aac", "amr", "mp3", "ogg", "wav", "silk",
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "7z",
        "apk", "epub", "csv",
    )

    fun classify(platform: SocialPlatform, path: String, extension: String = File(path).extension): SocialFileMatch? {
        val normalized = path.replace('\\', '/').lowercase(Locale.ROOT)
        val segments = normalized.split('/').filter(String::isNotBlank).toSet()
        val ext = extension.trim().trimStart('.').lowercase(Locale.ROOT)
        val appName = if (platform == SocialPlatform.QQ) "QQ" else "微信"

        if (segments.any(cacheSegments::contains) || ext in temporaryExtensions) {
            val category = if (platform == SocialPlatform.QQ) CleanCategory.QQ_CACHE else CleanCategory.WECHAT_CACHE
            return SocialFileMatch(category, "$appName 缓存或日志，可重新生成")
        }
        if (segments.any(mediaSegments::contains) || ext in mediaExtensions || (ext == "dat" && ("image2" in segments || "sns" in segments))) {
            val category = if (platform == SocialPlatform.QQ) CleanCategory.QQ_MEDIA else CleanCategory.WECHAT_MEDIA
            return SocialFileMatch(category, "$appName 图片、视频或语音，删除后不可恢复", selected = false)
        }
        if (segments.any(receivedFileSegments::contains) || ext in documentExtensions) {
            val category = if (platform == SocialPlatform.QQ) CleanCategory.QQ_FILES else CleanCategory.WECHAT_FILES
            return SocialFileMatch(category, "$appName 接收或聊天文件，请确认后处理", selected = false)
        }
        return null
    }
}

class SocialCleaner(private val context: Context) {
    private val roots get() = StorageAccess.roots(context)
    private val whitelist = WhitelistRepository(context)

    suspend fun scan(platform: SocialPlatform, onProgress: suspend (ScanProgress) -> Unit = {}): ScanReport {
        val excluded = whitelist.pathEntries()
        val policy = SafePathPolicy(roots, excluded, allowAndroidMedia = true)
        val scanRoots = findRoots(platform).filter(policy::canScan)
        val items = mutableListOf<CleanItem>()
        var scanned = 0L
        var scannedBytes = 0L
        var foundBytes = 0L
        var skipped = 0L
        var errors = 0L

        val elapsed = measureTimeMillis {
            val stack = ArrayDeque<File>().apply { scanRoots.forEach(::add) }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = stack.removeLast()
                val children = runCatching { directory.listFiles().orEmpty() }
                    .onFailure { errors++ }
                    .getOrDefault(emptyArray())
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    when {
                        Files.isSymbolicLink(child.toPath()) -> skipped++
                        child.isDirectory && policy.canScan(child) -> stack.add(child)
                        child.isFile && policy.canScan(child) && !whitelist.isProtected(child) -> {
                            scanned++
                            val size = child.length().coerceAtLeast(0)
                            scannedBytes += size
                            SocialFileClassifier.classify(platform, child.absolutePath)?.let { match ->
                                items += child.toItem(match)
                                foundBytes += size
                            }
                            if (scanned % PROGRESS_INTERVAL == 0L) {
                                onProgress(ScanProgress(child.absolutePath, scanned, items.size, foundBytes))
                            }
                        }
                        else -> skipped++
                    }
                }
            }
        }
        return ScanReport(items.sortedByDescending(CleanItem::size), scanned, scannedBytes, skipped, errors, elapsed)
    }

    private fun findRoots(platform: SocialPlatform): List<File> {
        val relativePaths = when (platform) {
            SocialPlatform.QQ -> listOf(
                "Tencent/MobileQQ",
                "Tencent/QQfile_recv",
                "Android/media/com.tencent.mobileqq",
            )
            SocialPlatform.WECHAT -> listOf(
                "Tencent/MicroMsg",
                "Android/media/com.tencent.mm",
            )
        }
        val candidates = roots.flatMap { root -> relativePaths.map { File(root, it.replace('/', File.separatorChar)) } }
            .filter { it.isDirectory && it.canRead() }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedBy { it.absolutePath.length }
        return candidates.filter { candidate ->
            candidates.none { parent -> parent != candidate && candidate.absolutePath.startsWith("${parent.absolutePath}${File.separator}") }
        }
    }

    private fun File.toItem(match: SocialFileMatch) = CleanItem(
        id = runCatching { canonicalPath }.getOrDefault(absolutePath),
        path = absolutePath,
        name = name.ifBlank { absolutePath },
        size = length().coerceAtLeast(0),
        modifiedAt = lastModified(),
        category = match.category,
        reason = match.reason,
        selected = match.selected,
    )

    private companion object { const val PROGRESS_INTERVAL = 250L }
}
