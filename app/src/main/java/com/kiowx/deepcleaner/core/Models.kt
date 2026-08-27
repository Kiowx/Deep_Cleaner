package com.kiowx.deepcleaner.core

import java.io.File

enum class MainSection { HOME, CLEAN, TOOLS, APPS, SETTINGS }

enum class ToolKind(val title: String, val subtitle: String) {
    STORAGE_ANALYZER("空间分析", "分类统计与目录占用排行"),
    SIMILAR_MEDIA("相似照片", "识别相似图、截图与模糊照片"),
    LARGE_FILES("大文件", "定位占用空间最多的文件"),
    DUPLICATES("重复文件", "SHA-256 内容复核，自动保留一份"),
    EMPTY_FOLDERS("空文件夹", "自底向上复核空目录"),
    DOWNLOADS("下载管理", "整理过期安装包与下载内容"),
    MEDIA_OPTIMIZER("媒体瘦身", "压缩大图片与视频并保留原文件"),
    APK_MANAGER("APK 管理", "识别旧版、重复和已安装安装包"),
    PRIVACY_SCAN("隐私检查", "查找日志、数据库和敏感导出文件"),
    WHITELIST("保护名单", "保护文件、目录和扩展名"),
    HISTORY("清理历史", "查看每次清理与释放空间"),
    EXTERNAL_STORAGE("外部存储", "连接 SD 卡、U 盘与云端目录"),
    SYSTEM_CACHE("系统缓存", "打开 Android 系统缓存清理"),
    TRASH("清理回收站", "恢复或彻底删除已清理项目"),
}

enum class CleanCategory(val title: String, val defaultSelected: Boolean) {
    TEMPORARY("临时文件", true),
    THUMBNAILS("缩略图缓存", true),
    LOGS("日志与崩溃记录", true),
    APP_CACHE("应用缓存", false),
    INSTALLERS("安装包", false),
    EMPTY_FILE("空文件", false),
    LARGE_FILE("大文件", false),
    DUPLICATE("重复文件", true),
    EMPTY_FOLDER("空文件夹", false),
    DOWNLOAD("下载内容", false),
    SIMILAR_MEDIA("相似媒体", false),
    MEDIA_OPTIMIZE("可压缩媒体", false),
    APK_ARCHIVE("安装包", false),
    PRIVACY_RISK("隐私残留", false),
}

data class CleanItem(
    val id: String,
    val path: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val category: CleanCategory,
    val reason: String,
    val selected: Boolean = category.defaultSelected,
    val duplicateGroup: String? = null,
    val duplicateReference: String? = null,
) {
    val file: File get() = File(path)
}

data class ScanReport(
    val items: List<CleanItem>,
    val scannedFiles: Long,
    val scannedBytes: Long,
    val skipped: Long,
    val errors: Long,
    val elapsedMs: Long,
)

data class ScanProgress(
    val currentPath: String = "",
    val scannedFiles: Long = 0,
    val foundItems: Int = 0,
    val foundBytes: Long = 0,
)

data class StorageSnapshot(
    val total: Long = 0,
    val available: Long = 0,
) {
    val used: Long get() = (total - available).coerceAtLeast(0)
    val usedFraction: Float get() = if (total <= 0) 0f else used.toFloat() / total.toFloat()
}

data class AppEntry(
    val packageName: String,
    val name: String,
    val version: String,
    val apkSize: Long,
    val isSystem: Boolean,
    val enabled: Boolean,
    val appBytes: Long = -1,
    val dataBytes: Long = -1,
    val cacheBytes: Long = -1,
    val lastUsedAt: Long = 0,
)

data class TrashRecord(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val deletedAt: Long,
)

enum class DeleteMode { TRASH, PERMANENT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ScheduleFrequency { DAILY, WEEKLY }

enum class StorageCategory(val title: String) {
    IMAGES("图片"), VIDEOS("视频"), AUDIO("音频"), DOCUMENTS("文档"),
    ARCHIVES("压缩包"), INSTALLERS("安装包"), OTHER("其他"),
}

data class StorageBucket(val category: StorageCategory, val bytes: Long, val files: Long)
data class DirectoryUsage(val path: String, val bytes: Long, val files: Long)
data class StorageAnalysis(
    val buckets: List<StorageBucket> = emptyList(),
    val directories: List<DirectoryUsage> = emptyList(),
    val scannedFiles: Long = 0,
    val scannedBytes: Long = 0,
)

enum class WhitelistType { PATH, EXTENSION, APP }
data class WhitelistEntry(val id: String, val type: WhitelistType, val value: String, val createdAt: Long)

data class CleanHistoryRecord(
    val id: String,
    val timestamp: Long,
    val source: String,
    val deleted: Int,
    val failed: Int,
    val releasedBytes: Long,
    val mode: DeleteMode,
)

data class SafRoot(val uri: String, val name: String)

data class OptimizeResult(val completed: Int, val failed: Int, val originalBytes: Long, val outputBytes: Long) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0)
}
