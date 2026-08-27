package com.kiowx.deepcleaner.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
    val mandatory: Boolean,
    val publishedAt: String,
)

internal object UpdateManifestParser {
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")
    private val versionNamePattern = Regex("^[0-9A-Za-z][0-9A-Za-z._-]{0,31}$")
    private const val releasePathPrefix = "/Kiowx/Deep_Cleaner/releases/download/"

    fun parse(raw: String): AppUpdateInfo {
        require(raw.length <= AppUpdateRepository.MAX_MANIFEST_BYTES) { "更新配置文件过大" }
        val root = JSONObject(raw)
        require(root.optString("format") == "deep-cleaner-update") { "更新配置格式不受支持" }
        require(root.optInt("schemaVersion") == 1) { "更新配置版本不受支持" }
        val versionCode = root.optLong("versionCode")
        val versionName = root.optString("versionName").trim()
        val minSdk = root.optInt("minSdk", 26)
        val apkUrl = root.optString("apkUrl").trim()
        val sha256 = root.optString("sha256").lowercase()
        require(versionCode > 0) { "versionCode 无效" }
        require(versionNamePattern.matches(versionName)) { "versionName 无效" }
        require(minSdk in 26..100) { "minSdk 无效" }
        require(sha256Pattern.matches(sha256)) { "SHA-256 无效" }
        val uri = URI(apkUrl)
        require(uri.scheme.equals("https", true)) { "APK 必须使用 HTTPS" }
        require(uri.host.equals("github.com", true) && uri.path.startsWith(releasePathPrefix)) {
            "APK 必须来自 Deep Cleaner 的 GitHub Release"
        }
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            apkUrl = apkUrl,
            sha256 = sha256,
            changelog = parseChangelog(root.opt("changelog")),
            mandatory = root.optBoolean("mandatory", false),
            publishedAt = root.optString("publishedAt").take(64),
        )
    }

    fun isNewer(info: AppUpdateInfo, currentVersionCode: Long, deviceApi: Int): Boolean =
        info.versionCode > currentVersionCode && info.minSdk <= deviceApi

    private fun parseChangelog(value: Any?): String {
        val lines = when (value) {
            is JSONArray -> buildList {
                for (index in 0 until minOf(value.length(), 12)) {
                    value.optString(index).trim().takeIf(String::isNotBlank)?.let { add(it.take(300)) }
                }
            }
            is String -> value.lineSequence().map(String::trim).filter(String::isNotBlank).take(12).map { it.take(300) }.toList()
            else -> emptyList()
        }
        return lines.joinToString("\n").take(4_000).ifBlank { "修复问题并提升使用体验。" }
    }
}

class AppUpdateRepository(private val context: Context) {
    companion object {
        const val UPDATE_MANIFEST_URL =
            "https://raw.githubusercontent.com/Kiowx/Deep_Cleaner/main/update/update.json"
        const val MAX_MANIFEST_BYTES = 262_144
        private const val MAX_APK_BYTES = 300L * 1024 * 1024
        private val allowedDownloadHosts = setOf(
            "github.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
    }

    val currentVersionCode: Long
        get() = packageVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    val currentVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().ifBlank { "未知" }

    fun check(): AppUpdateInfo? {
        val info = UpdateManifestParser.parse(fetchText(UPDATE_MANIFEST_URL, MAX_MANIFEST_BYTES))
        if (info.versionCode > currentVersionCode && info.minSdk > Build.VERSION.SDK_INT) {
            error("新版本需要 Android API ${info.minSdk} 或更高版本")
        }
        return info.takeIf { UpdateManifestParser.isNewer(it, currentVersionCode, Build.VERSION.SDK_INT) }
    }

    fun download(info: AppUpdateInfo, onProgress: (Int) -> Unit = {}): File {
        require(UpdateManifestParser.isNewer(info, currentVersionCode, Build.VERSION.SDK_INT)) { "该版本无需更新" }
        val directory = File(context.cacheDir, "updates").apply {
            require(mkdirs() || isDirectory) { "无法创建更新缓存目录" }
            listFiles()?.forEach { it.delete() }
        }
        val temporary = File(directory, "Deep-Cleaner-${info.versionName}.apk.part")
        val target = File(directory, "Deep-Cleaner-${info.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openConnection(info.apkUrl)
        try {
            val expectedLength = connection.contentLengthLong
            require(expectedLength <= 0 || expectedLength <= MAX_APK_BYTES) { "更新包超过大小限制" }
            var copied = 0L
            var lastProgress = -1
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        require(copied <= MAX_APK_BYTES) { "更新包超过大小限制" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val progress = if (expectedLength > 0) ((copied * 100) / expectedLength).toInt().coerceIn(0, 99) else 0
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
            require(copied > 0) { "下载的更新包为空" }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha256.equals(info.sha256, true)) { "更新包 SHA-256 校验失败" }
            verifyArchive(temporary, info)
            require(temporary.renameTo(target)) { "无法保存更新包" }
            onProgress(100)
            return target
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installIntent(file: File): Intent {
        require(file.isFile && file.parentFile?.name == "updates") { "更新包不存在" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update-files", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun verifyArchive(file: File, info: AppUpdateInfo) {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        } ?: error("下载内容不是有效 APK")
        require(packageInfo.packageName == context.packageName) { "更新包应用 ID 不匹配" }
        require(packageVersionCode(packageInfo) == info.versionCode) { "更新包版本号与配置不匹配" }
    }

    private fun fetchText(url: String, limit: Int): String {
        val connection = openConnection(url)
        try {
            val expectedLength = connection.contentLengthLong
            require(expectedLength <= 0 || expectedLength <= limit) { "更新配置文件过大" }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= limit) { "更新配置文件过大" }
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(initialUrl: String): HttpURLConnection {
        var current = URL(initialUrl)
        repeat(6) {
            require(current.protocol.equals("https", true)) { "更新地址必须使用 HTTPS" }
            require(current.host.lowercase() in allowedDownloadHosts) { "更新下载域名不受信任" }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 45_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, application/octet-stream")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Deep-Cleaner-Android/${currentVersionName}")
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: error("更新地址重定向无效")
                    current = URL(current, location)
                    connection.disconnect()
                }
                else -> {
                    val code = connection.responseCode
                    connection.disconnect()
                    error("GitHub 更新服务返回 HTTP $code")
                }
            }
        }
        error("更新地址重定向次数过多")
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}

class UpdateFileProvider : FileProvider()
