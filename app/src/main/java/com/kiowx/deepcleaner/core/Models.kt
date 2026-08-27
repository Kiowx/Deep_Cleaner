package com.kiowx.deepcleaner.core

import java.io.File

enum class MainSection { HOME, CLEAN, TOOLS, APPS, SETTINGS }

enum class ToolKind(val title: String, val subtitle: String) {
    STORAGE_ANALYZER("空间分析", "分类统计与目录占用排行"),
    SIMILAR_MEDIA("相似照片", "识别相似图、截图与模糊照片"),
    MEDIA_COLLECTIONS("截图与录屏", "按时间整理截图、录屏和屏幕录像"),
    VIDEO_DUPLICATES("重复视频", "使用媒体信息和内容摘要识别重复视频"),
    LARGE_FILES("大文件", "定位占用空间最多的文件"),
    DUPLICATES("重复文件", "SHA-256 内容复核，自动保留一份"),
    EMPTY_FOLDERS("空目录与残留", "合并检查空目录和已卸载应用残留"),
    DOWNLOADS("下载管理", "整理过期安装包与下载内容"),
    ARCHIVE_MANAGER("压缩包检查", "识别重复、已解压和长期未用压缩包"),
    FILE_TIMELINE("文件时间线", "查看最近新增和长期未修改的大文件"),
    MEDIA_OPTIMIZER("媒体瘦身", "压缩大图片与视频并保留原文件"),
    APK_MANAGER("APK 管理", "识别旧版、重复和已安装安装包"),
    PRIVACY_SCAN("隐私检查", "查找日志、数据库和敏感导出文件"),
    CUSTOM_RULES("自定义规则", "按路径、类型、大小和时间建立清理规则"),
    CLEAN_PROFILES("清理方案", "安全清理、最大释放与下载整理方案"),
    STORAGE_TRENDS("存储趋势", "记录空间变化并定位异常增长"),
    VAULT("文件保险箱", "加密保存重要文件并使用系统身份验证"),
    CONFIG_BACKUP("配置备份", "导入或导出规则、保护名单和设置"),
    RULE_UPDATES("规则更新", "校验签名后更新本地扫描规则"),
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
    MEDIA_COLLECTION("截图与录屏", false),
    VIDEO_DUPLICATE("重复视频", false),
    APP_RESIDUAL("卸载残留", false),
    ARCHIVE_CANDIDATE("压缩包", false),
    FILE_TIMELINE("时间线文件", false),
    CUSTOM_RULE("自定义规则", false),
    ROOT_CACHE("Root 私有缓存", false),
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
    val risk: CleanRisk
        get() = when (category) {
            CleanCategory.TEMPORARY, CleanCategory.THUMBNAILS, CleanCategory.LOGS,
            CleanCategory.DUPLICATE, CleanCategory.EMPTY_FOLDER -> CleanRisk.LOW
            CleanCategory.APP_CACHE, CleanCategory.INSTALLERS, CleanCategory.DOWNLOAD,
            CleanCategory.APK_ARCHIVE, CleanCategory.MEDIA_COLLECTION, CleanCategory.VIDEO_DUPLICATE,
            CleanCategory.ARCHIVE_CANDIDATE, CleanCategory.CUSTOM_RULE, CleanCategory.ROOT_CACHE -> CleanRisk.MEDIUM
            else -> CleanRisk.HIGH
        }
    val impact: String
        get() = when (category) {
            CleanCategory.DUPLICATE, CleanCategory.VIDEO_DUPLICATE -> "删除候选副本，保留内容一致的参考文件"
            CleanCategory.EMPTY_FOLDER -> "只移除当前仍为空的目录"
            CleanCategory.APP_RESIDUAL -> "目录可能包含已卸载应用留下的用户文件"
            CleanCategory.ROOT_CACHE -> "应用下次启动会重新生成缓存，可能需要重新加载内容"
            CleanCategory.MEDIA_OPTIMIZE -> "生成压缩副本，不替换原文件"
            else -> "删除后将释放对应空间；永久删除模式下无法恢复"
        }
}

enum class CleanRisk(val title: String) { LOW("低风险"), MEDIUM("需确认"), HIGH("高风险") }
enum class ResultSort { SIZE_DESC, NEWEST, OLDEST, NAME }
enum class CleanProfile(val title: String, val subtitle: String) {
    SAFE("安全清理", "只默认选择低风险缓存、日志和确认过的重复副本"),
    MAX_SPACE("最大释放", "选择更多候选项，执行前仍需手动确认"),
    DOWNLOADS_ONLY("下载整理", "只扫描下载目录中的过期文件和安装包"),
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
    val categories: String = "",
    val durationMs: Long = 0,
)

data class CustomCleanRule(
    val id: String,
    val name: String,
    val pathContains: String = "",
    val extensions: Set<String> = emptySet(),
    val minimumBytes: Long = 0,
    val olderThanDays: Int = 0,
    val enabled: Boolean = true,
    val safeByDefault: Boolean = false,
    val source: String = "本地",
)

data class StorageTrendPoint(val timestamp: Long, val usedBytes: Long, val availableBytes: Long)
data class VaultEntry(val id: String, val name: String, val size: Long, val addedAt: Long, val mimeType: String)
data class RuleUpdateInfo(val version: Int = 0, val updatedAt: Long = 0, val ruleCount: Int = 0)

data class SafRoot(val uri: String, val name: String)

data class OptimizeResult(val completed: Int, val failed: Int, val originalBytes: Long, val outputBytes: Long) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0)
}
