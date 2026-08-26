package com.kiowx.deepcleaner.core

import java.io.File
import java.util.Locale

object FileClassifier {
    private val tempExtensions = setOf("tmp", "temp", "part", "partial", "crdownload", "download")
    private val logExtensions = setOf("log", "trace", "dmp", "stacktrace")
    private val installerExtensions = setOf("apk", "apks", "xapk", "apkm")
    private val thumbnailNames = setOf(".thumbdata3", ".thumbdata4", ".thumbdata5", ".thumbdata6")

    data class Match(val category: CleanCategory, val reason: String, val safeByDefault: Boolean)

    fun classify(file: File, now: Long = System.currentTimeMillis()): Match? {
        if (!file.isFile) return null
        val name = file.name.lowercase(Locale.ROOT)
        val extension = file.extension.lowercase(Locale.ROOT)
        val normalized = file.absolutePath.replace('\\', '/').lowercase(Locale.ROOT)
        val ageMs = (now - file.lastModified()).coerceAtLeast(0)
        val olderThanDay = ageMs >= 24L * 60 * 60 * 1000
        val olderThanWeek = ageMs >= 7L * 24 * 60 * 60 * 1000

        if (("/.thumbnails/" in normalized || thumbnailNames.any(name::startsWith)) && olderThanDay) {
            return Match(CleanCategory.THUMBNAILS, "媒体缩略图缓存", true)
        }
        if (extension in tempExtensions && olderThanDay) {
            return Match(CleanCategory.TEMPORARY, "超过 24 小时的临时文件", true)
        }
        if (extension in logExtensions && olderThanWeek) {
            return Match(CleanCategory.LOGS, "超过 7 天的日志或崩溃记录", true)
        }
        if (("/cache/" in normalized || "/.cache/" in normalized) && olderThanWeek) {
            return Match(CleanCategory.APP_CACHE, "超过 7 天的缓存文件", false)
        }
        if (extension in installerExtensions && olderThanWeek) {
            return Match(CleanCategory.INSTALLERS, "超过 7 天的安装包", false)
        }
        if (file.length() == 0L && olderThanWeek) {
            return Match(CleanCategory.EMPTY_FILE, "超过 7 天的空文件", false)
        }
        return null
    }

    fun isInstaller(file: File): Boolean = file.extension.lowercase(Locale.ROOT) in installerExtensions
}

