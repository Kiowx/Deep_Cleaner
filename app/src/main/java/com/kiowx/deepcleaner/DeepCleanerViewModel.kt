package com.kiowx.deepcleaner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiowx.deepcleaner.core.AppEntry
import com.kiowx.deepcleaner.core.AppPreferences
import com.kiowx.deepcleaner.core.AppRepository
import com.kiowx.deepcleaner.core.AdvancedScanner
import com.kiowx.deepcleaner.core.CleanItem
import com.kiowx.deepcleaner.core.CleanProfile
import com.kiowx.deepcleaner.core.CleanerEngine
import com.kiowx.deepcleaner.core.CleanHistoryRecord
import com.kiowx.deepcleaner.core.DeleteMode
import com.kiowx.deepcleaner.core.CustomCleanRule
import com.kiowx.deepcleaner.core.CustomRuleRepository
import com.kiowx.deepcleaner.core.ConfigRepository
import com.kiowx.deepcleaner.core.ExpansionScanner
import com.kiowx.deepcleaner.core.MainSection
import com.kiowx.deepcleaner.core.HistoryRepository
import com.kiowx.deepcleaner.core.MediaOptimizer
import com.kiowx.deepcleaner.core.ScanProgress
import com.kiowx.deepcleaner.core.ScanReport
import com.kiowx.deepcleaner.core.ScheduleFrequency
import com.kiowx.deepcleaner.core.StorageAccess
import com.kiowx.deepcleaner.core.StorageSnapshot
import com.kiowx.deepcleaner.core.StorageAnalysis
import com.kiowx.deepcleaner.core.StorageTrendPoint
import com.kiowx.deepcleaner.core.StorageTrendRepository
import com.kiowx.deepcleaner.core.ThemeMode
import com.kiowx.deepcleaner.core.ToolKind
import com.kiowx.deepcleaner.core.TrashManager
import com.kiowx.deepcleaner.core.TrashRecord
import com.kiowx.deepcleaner.core.VaultEntry
import com.kiowx.deepcleaner.core.VaultRepository
import com.kiowx.deepcleaner.core.RootAccess
import com.kiowx.deepcleaner.core.RuleUpdateInfo
import com.kiowx.deepcleaner.core.RuleUpdateRepository
import com.kiowx.deepcleaner.core.SafRepository
import com.kiowx.deepcleaner.core.SafRoot
import com.kiowx.deepcleaner.core.WhitelistEntry
import com.kiowx.deepcleaner.core.WhitelistRepository
import com.kiowx.deepcleaner.core.WhitelistType
import com.kiowx.deepcleaner.core.formatBytes
import com.kiowx.deepcleaner.worker.ScheduleManager
import com.kiowx.deepcleaner.widget.CleanerWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeepCleanerUiState(
    val section: MainSection = MainSection.HOME,
    val activeTool: ToolKind? = null,
    val hasStorageAccess: Boolean = false,
    val storage: StorageSnapshot = StorageSnapshot(),
    val isBusy: Boolean = false,
    val operationTitle: String = "",
    val progress: ScanProgress = ScanProgress(),
    val report: ScanReport? = null,
    val items: List<CleanItem> = emptyList(),
    val apps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = false,
    val trash: List<TrashRecord> = emptyList(),
    val storageAnalysis: StorageAnalysis? = null,
    val history: List<CleanHistoryRecord> = emptyList(),
    val whitelist: List<WhitelistEntry> = emptyList(),
    val safRoots: List<SafRoot> = emptyList(),
    val safAnalysis: StorageAnalysis? = null,
    val customRules: List<CustomCleanRule> = emptyList(),
    val storageTrends: List<StorageTrendPoint> = emptyList(),
    val vaultEntries: List<VaultEntry> = emptyList(),
    val ruleUpdateInfo: RuleUpdateInfo = RuleUpdateInfo(),
    val cleanProfile: CleanProfile = CleanProfile.SAFE,
    val rootModeEnabled: Boolean = false,
    val rootAvailable: Boolean? = null,
    val hasUsageAccess: Boolean = false,
    val message: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val deleteMode: DeleteMode = DeleteMode.PERMANENT,
    val haptics: Boolean = true,
    val largeFileMb: Int = 256,
    val scheduleEnabled: Boolean = false,
    val scheduleFrequency: ScheduleFrequency = ScheduleFrequency.WEEKLY,
    val scheduleRequireCharging: Boolean = true,
    val scheduleRequireIdle: Boolean = true,
    val scheduleScanOnly: Boolean = true,
    val scheduleStorageThreshold: Int = 85,
    val trashRetentionDays: Int = 30,
    val trashMaxMb: Int = 2048,
    val lastCleanedBytes: Long = 0,
    val lastCleanedAt: Long = 0,
) {
    val selectedItems: List<CleanItem> get() = items.filter(CleanItem::selected)
    val selectedBytes: Long get() = selectedItems.sumOf(CleanItem::size)
}

class DeepCleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val engine = CleanerEngine(application)
    private val trashManager = TrashManager(application)
    private val appRepository = AppRepository(application)
    private val advancedScanner = AdvancedScanner(application)
    private val expansionScanner = ExpansionScanner(application)
    private val historyRepository = HistoryRepository(application)
    private val whitelistRepository = WhitelistRepository(application)
    private val safRepository = SafRepository(application)
    private val mediaOptimizer = MediaOptimizer(application)
    private val customRuleRepository = CustomRuleRepository(application)
    private val trendRepository = StorageTrendRepository(application)
    private val vaultRepository = VaultRepository(application)
    private val configRepository = ConfigRepository(application)
    private val ruleUpdateRepository = RuleUpdateRepository(application)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(
        DeepCleanerUiState(
            hasStorageAccess = StorageAccess.hasAllFilesAccess(),
            storage = StorageAccess.snapshot(application),
            themeMode = preferences.themeMode,
            deleteMode = preferences.deleteMode,
            haptics = preferences.haptics,
            largeFileMb = preferences.largeFileMb,
            scheduleEnabled = preferences.scheduleEnabled,
            scheduleFrequency = preferences.scheduleFrequency,
            scheduleRequireCharging = preferences.scheduleRequireCharging,
            scheduleRequireIdle = preferences.scheduleRequireIdle,
            scheduleScanOnly = preferences.scheduleScanOnly,
            scheduleStorageThreshold = preferences.scheduleStorageThreshold,
            trashRetentionDays = preferences.trashRetentionDays,
            trashMaxMb = preferences.trashMaxMb,
            lastCleanedBytes = preferences.lastCleanedBytes,
            lastCleanedAt = preferences.lastCleanedAt,
            trash = trashManager.list(),
            history = historyRepository.list(),
            whitelist = whitelistRepository.list(),
            safRoots = safRepository.roots(),
            customRules = customRuleRepository.list(),
            storageTrends = trendRepository.list(),
            vaultEntries = vaultRepository.list(),
            ruleUpdateInfo = ruleUpdateRepository.info(),
            cleanProfile = preferences.cleanProfile,
            rootModeEnabled = preferences.rootModeEnabled,
            hasUsageAccess = appRepository.hasUsageAccess(),
        ),
    )
    val state: StateFlow<DeepCleanerUiState> = _state.asStateFlow()

    init {
        trendRepository.record(_state.value.storage)
        _state.update { it.copy(storageTrends = trendRepository.list()) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = trashManager.prune(preferences.trashRetentionDays, preferences.trashMaxMb * 1024L * 1024L)
            if (result.deleted > 0) {
                _state.update { it.copy(trash = trashManager.list(), message = "回收站已自动释放 ${formatBytes(result.releasedBytes)}") }
            }
        }
    }

    fun refreshPermissionAndStorage() {
        val usageGranted = appRepository.hasUsageAccess()
        val usageChanged = usageGranted && !_state.value.hasUsageAccess
        _state.update {
            val snapshot = StorageAccess.snapshot(getApplication())
            trendRepository.record(snapshot)
            it.copy(
                hasStorageAccess = StorageAccess.hasAllFilesAccess(),
                storage = snapshot,
                storageTrends = trendRepository.list(),
                hasUsageAccess = usageGranted,
            )
        }
        CleanerWidgetProvider.updateAll(getApplication())
        if (usageChanged && _state.value.section == MainSection.APPS) loadApps()
    }

    fun selectSection(section: MainSection) {
        _state.update { it.copy(section = section, activeTool = null, message = null) }
        if (section == MainSection.APPS && _state.value.apps.isEmpty()) loadApps()
    }

    fun openTool(tool: ToolKind) {
        _state.update { it.copy(section = MainSection.TOOLS, activeTool = tool, items = emptyList(), report = null, storageAnalysis = null) }
        if (tool == ToolKind.TRASH) refreshTrash()
        if (tool == ToolKind.HISTORY) _state.update { it.copy(history = historyRepository.list()) }
        if (tool == ToolKind.WHITELIST) _state.update { it.copy(whitelist = whitelistRepository.list()) }
        if (tool == ToolKind.EXTERNAL_STORAGE) _state.update { it.copy(safRoots = safRepository.roots(), safAnalysis = null) }
        if (tool == ToolKind.CUSTOM_RULES) _state.update { it.copy(customRules = customRuleRepository.list()) }
        if (tool == ToolKind.STORAGE_TRENDS) _state.update { it.copy(storageTrends = trendRepository.list()) }
        if (tool == ToolKind.VAULT) _state.update { it.copy(vaultEntries = vaultRepository.list()) }
        if (tool == ToolKind.RULE_UPDATES) _state.update { it.copy(ruleUpdateInfo = ruleUpdateRepository.info()) }
        if (tool == ToolKind.ROOT_CLEANER && _state.value.rootAvailable == null) checkRootAccess()
    }

    fun closeTool() {
        if (_state.value.isBusy) cancelOperation()
        _state.update { it.copy(activeTool = null, items = emptyList(), report = null, storageAnalysis = null, progress = ScanProgress()) }
    }

    fun runQuickScan() {
        if (!requireStorageAccess()) return
        when (_state.value.cleanProfile) {
            CleanProfile.DOWNLOADS_ONLY -> runScan("正在整理下载") { engine.scanDownloads(onProgress = it) }
            CleanProfile.SAFE -> runScan("正在深度扫描") { onProgress -> engine.scanJunk(onProgress) }
            CleanProfile.MAX_SPACE -> runScan("正在扫描最大释放方案") { onProgress ->
                val report = engine.scanJunk(onProgress)
                report.copy(items = report.items.map { item -> item.copy(selected = item.risk != com.kiowx.deepcleaner.core.CleanRisk.HIGH) })
            }
        }
    }

    fun runSafeScan() {
        if (!requireStorageAccess()) return
        runScan("正在执行安全扫描") { onProgress -> engine.scanJunk(onProgress) }
    }

    fun runActiveTool() {
        val tool = _state.value.activeTool ?: return
        if (tool == ToolKind.ROOT_CLEANER) {
            if (!_state.value.rootModeEnabled) return postMessage("请先开启 Root 高级模式")
            runScan("正在读取 Root 私有缓存") { RootAccess.scanCaches(getApplication()) }
            return
        }
        if (tool in setOf(ToolKind.CLEAN_PROFILES, ToolKind.STORAGE_TRENDS, ToolKind.VAULT, ToolKind.CONFIG_BACKUP, ToolKind.RULE_UPDATES)) return
        if (!requireStorageAccess()) return
        when (tool) {
            ToolKind.STORAGE_ANALYZER -> runStorageAnalysis()
            ToolKind.SIMILAR_MEDIA -> runScan("正在分析相似照片") { advancedScanner.scanSimilarMedia(it) }
            ToolKind.MEDIA_COLLECTIONS -> runScan("正在整理截图与录屏") { expansionScanner.scanScreenMedia(it) }
            ToolKind.VIDEO_DUPLICATES -> runScan("正在校验重复视频") { expansionScanner.scanDuplicateVideos(it) }
            ToolKind.LARGE_FILES -> runScan("正在扫描大文件") { onProgress ->
                engine.scanLargeFiles(_state.value.largeFileMb * 1024L * 1024L, onProgress = onProgress)
            }
            ToolKind.DUPLICATES -> runScan("正在校验重复文件") { engine.scanDuplicates(onProgress = it) }
            ToolKind.EMPTY_FOLDERS -> runScan("正在检查空目录与残留") {
                ExpansionScanner.mergeReports(engine.scanEmptyFolders(it), expansionScanner.scanResiduals(it))
            }
            ToolKind.DOWNLOADS -> runScan("正在整理下载目录") { engine.scanDownloads(onProgress = it) }
            ToolKind.ARCHIVE_MANAGER -> runScan("正在检查压缩包") { expansionScanner.scanArchives(it) }
            ToolKind.FILE_TIMELINE -> runScan("正在建立文件时间线") { expansionScanner.scanTimeline(it) }
            ToolKind.MEDIA_OPTIMIZER -> runScan("正在查找可压缩媒体") { advancedScanner.scanMediaForOptimization(it) }
            ToolKind.APK_MANAGER -> runScan("正在分析安装包") { advancedScanner.scanApkArchives(it) }
            ToolKind.PRIVACY_SCAN -> runScan("正在检查隐私残留") { advancedScanner.scanPrivacyRisks(it) }
            ToolKind.CUSTOM_RULES -> runScan("正在执行自定义规则") {
                expansionScanner.scanCustomRules(customRuleRepository.list() + ruleUpdateRepository.rules(), it)
            }
            ToolKind.WHITELIST, ToolKind.HISTORY, ToolKind.EXTERNAL_STORAGE, ToolKind.SYSTEM_CACHE,
            ToolKind.CLEAN_PROFILES, ToolKind.STORAGE_TRENDS, ToolKind.VAULT, ToolKind.CONFIG_BACKUP,
            ToolKind.RULE_UPDATES, ToolKind.ROOT_CLEANER -> Unit
            ToolKind.TRASH -> refreshTrash()
        }
    }

    private fun runStorageAnalysis() {
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, operationTitle = "正在分析存储空间", progress = ScanProgress(), storageAnalysis = null, message = null) }
        activeJob = viewModelScope.launch {
            try {
                val analysis = withContext(Dispatchers.IO) {
                    advancedScanner.analyzeStorage { progress -> _state.update { it.copy(progress = progress) } }
                }
                _state.update { it.copy(isBusy = false, storageAnalysis = analysis, message = "已分析 ${analysis.scannedFiles} 个文件") }
            } catch (_: CancellationException) {
                _state.update { it.copy(isBusy = false, message = "分析已停止") }
            } catch (error: Throwable) {
                _state.update { it.copy(isBusy = false, message = "分析失败：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    private fun runScan(
        title: String,
        block: suspend (suspend (ScanProgress) -> Unit) -> ScanReport,
    ) {
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, operationTitle = title, progress = ScanProgress(), items = emptyList(), report = null, message = null) }
        activeJob = viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    block { progress -> _state.update { it.copy(progress = progress) } }
                }
                _state.update {
                    preferences.lastScanBytes = report.items.sumOf(CleanItem::size)
                    preferences.lastScanAt = System.currentTimeMillis()
                    it.copy(
                        isBusy = false,
                        items = report.items,
                        report = report,
                        progress = ScanProgress(foundItems = report.items.size, foundBytes = report.items.sumOf(CleanItem::size)),
                        message = if (report.items.isEmpty()) "扫描完成，暂未发现可处理项目" else "发现 ${report.items.size} 项，共 ${formatBytes(report.items.sumOf(CleanItem::size))}",
                    )
                }
                CleanerWidgetProvider.updateAll(getApplication())
            } catch (_: CancellationException) {
                _state.update { it.copy(isBusy = false, message = "操作已停止") }
            } catch (error: Throwable) {
                _state.update { it.copy(isBusy = false, message = "扫描失败：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    fun cancelOperation() {
        activeJob?.cancel()
        activeJob = null
    }

    fun toggleItem(id: String) {
        _state.update { state -> state.copy(items = state.items.map { if (it.id == id) it.copy(selected = !it.selected) else it }) }
    }

    fun selectAll(selected: Boolean) {
        _state.update { state -> state.copy(items = state.items.map { it.copy(selected = selected) }) }
    }

    fun selectCategory(categoryName: String, selected: Boolean) {
        _state.update { state ->
            state.copy(items = state.items.map { if (it.category.name == categoryName) it.copy(selected = selected) else it })
        }
    }

    fun cleanSelected() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) {
            postMessage("请先选择要处理的项目")
            return
        }
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, operationTitle = "正在安全清理", progress = ScanProgress(), message = null) }
        activeJob = viewModelScope.launch {
            try {
                val mode = _state.value.deleteMode
                val started = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    engine.deleteItems(selected, mode, trashManager) { done, total, current ->
                        _state.update {
                            it.copy(progress = ScanProgress(current, done.toLong(), done, selected.take(done).sumOf(CleanItem::size)))
                        }
                    }
                }
                preferences.lastCleanedBytes = result.releasedBytes
                preferences.lastCleanedAt = System.currentTimeMillis()
                historyRepository.record(
                    _state.value.activeTool?.title ?: _state.value.cleanProfile.title,
                    result,
                    mode,
                    selected,
                    System.currentTimeMillis() - started,
                )
                trashManager.prune(preferences.trashRetentionDays, preferences.trashMaxMb * 1024L * 1024L)
                trendRepository.record(StorageAccess.snapshot(getApplication()))
                _state.update { state ->
                    state.copy(
                        isBusy = false,
                        items = state.items.filter { item -> selected.none { it.id == item.id } || item.file.exists() },
                        storage = StorageAccess.snapshot(getApplication()),
                        trash = trashManager.list(),
                        lastCleanedBytes = result.releasedBytes,
                        lastCleanedAt = preferences.lastCleanedAt,
                        history = historyRepository.list(),
                        storageTrends = trendRepository.list(),
                        message = "已处理 ${result.deleted} 项，释放 ${formatBytes(result.releasedBytes)}" +
                            if (result.failed > 0) "，${result.failed} 项因安全复核失败而跳过" else "",
                    )
                }
                CleanerWidgetProvider.updateAll(getApplication())
            } catch (_: CancellationException) {
                _state.update { it.copy(isBusy = false, trash = trashManager.list(), message = "清理已停止") }
            } catch (error: Throwable) {
                _state.update { it.copy(isBusy = false, message = "清理失败：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    fun refreshTrash() = _state.update { it.copy(trash = trashManager.list()) }

    fun restoreTrash(record: TrashRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = trashManager.restore(record)
            _state.update { it.copy(trash = trashManager.list(), message = if (ok) "已恢复 ${record.name}" else "恢复失败") }
        }
    }

    fun deleteTrash(record: TrashRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = trashManager.permanentlyDelete(record)
            _state.update { it.copy(trash = trashManager.list(), message = if (ok) "已永久删除 ${record.name}" else "删除失败") }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = trashManager.empty()
            _state.update { it.copy(trash = trashManager.list(), message = "回收站已释放 ${formatBytes(result.releasedBytes)}") }
        }
    }

    fun optimizeSelectedMedia() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return postMessage("请先选择要压缩的媒体")
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, operationTitle = "正在生成压缩副本", progress = ScanProgress(), message = null) }
        activeJob = viewModelScope.launch {
            val result = runCatching {
                mediaOptimizer.optimize(selected) { done, _, current ->
                    _state.update { it.copy(progress = ScanProgress(current, done.toLong(), done, 0)) }
                }
            }
            _state.update {
                val value = result.getOrNull()
                it.copy(
                    isBusy = false,
                    message = if (value != null) "已生成 ${value.completed} 个压缩副本，预计节省 ${formatBytes(value.savedBytes)}；原文件未改动" else "压缩失败：${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    fun archiveSelectedDownloads() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return postMessage("请先选择要归档的下载文件")
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) { advancedScanner.archiveDownloads(selected) }
            _state.update { state -> state.copy(items = state.items.filterNot { item -> selected.any { it.id == item.id } && !item.file.exists() }, message = "已归档 $moved 个文件") }
        }
    }

    fun addWhitelist(type: WhitelistType, value: String) {
        val added = whitelistRepository.add(type, value)
        _state.update { it.copy(whitelist = whitelistRepository.list(), message = if (added != null) "已加入保护名单" else "保护项格式无效") }
    }

    fun removeWhitelist(id: String) {
        whitelistRepository.remove(id)
        _state.update { it.copy(whitelist = whitelistRepository.list(), message = "已移除保护项") }
    }

    fun clearHistory() {
        historyRepository.clear()
        _state.update { it.copy(history = emptyList(), message = "清理历史已清空") }
    }

    fun addCustomRule(name: String, path: String, extensions: String, minimumMb: Int, olderDays: Int, safe: Boolean) {
        val added = customRuleRepository.add(name, path, extensions, minimumMb, olderDays, safe)
        _state.update { it.copy(customRules = customRuleRepository.list(), message = if (added == null) "规则至少需要路径或扩展名条件" else "已添加规则 ${added.name}") }
    }

    fun setCustomRuleEnabled(id: String, enabled: Boolean) {
        customRuleRepository.setEnabled(id, enabled)
        _state.update { it.copy(customRules = customRuleRepository.list()) }
    }

    fun removeCustomRule(id: String) {
        customRuleRepository.remove(id)
        _state.update { it.copy(customRules = customRuleRepository.list(), message = "规则已删除") }
    }

    fun setCleanProfile(profile: CleanProfile) {
        preferences.cleanProfile = profile
        _state.update { it.copy(cleanProfile = profile, message = "已切换到${profile.title}") }
    }

    fun setRootMode(enabled: Boolean) {
        preferences.rootModeEnabled = enabled
        _state.update { it.copy(rootModeEnabled = enabled) }
        if (enabled) checkRootAccess()
    }

    fun checkRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = RootAccess.isAvailable()
            _state.update { it.copy(rootAvailable = available, message = if (available) "Root 授权可用" else "未获得 Root 授权") }
        }
    }

    fun clearStorageTrends() {
        trendRepository.clear()
        trendRepository.record(StorageAccess.snapshot(getApplication()))
        _state.update { it.copy(storageTrends = trendRepository.list(), message = "存储趋势已重置") }
    }

    fun importVaultFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { vaultRepository.import(uri) }
            _state.update { it.copy(vaultEntries = vaultRepository.list(), message = result.fold({ entry -> "已加密保存 ${entry.name}" }, { error -> "导入失败：${error.message}" })) }
        }
    }

    fun exportVaultFile(entry: VaultEntry, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { vaultRepository.export(entry, uri) }
            _state.update { it.copy(message = result.fold({ "已导出 ${entry.name}" }, { error -> "导出失败：${error.message}" })) }
        }
    }

    fun deleteVaultFile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = vaultRepository.delete(id)
            _state.update { it.copy(vaultEntries = vaultRepository.list(), message = if (ok) "保险箱文件已删除" else "删除失败") }
        }
    }

    fun exportConfig(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(configRepository.export()) }
                    ?: error("无法写入配置文件")
            }
            _state.update { it.copy(message = result.fold({ "配置已导出" }, { error -> "导出失败：${error.message}" })) }
        }
    }

    fun importConfig(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取配置文件")
                configRepository.import(raw)
            }
            val imported = result.getOrNull()
            if (imported != null) {
                ScheduleManager.configure(
                    getApplication(), preferences.scheduleEnabled, preferences.scheduleFrequency,
                    preferences.scheduleRequireCharging, preferences.scheduleRequireIdle,
                )
            }
            _state.update {
                it.copy(
                    customRules = customRuleRepository.list(), whitelist = whitelistRepository.list(),
                    themeMode = preferences.themeMode, deleteMode = preferences.deleteMode,
                    cleanProfile = preferences.cleanProfile,
                    haptics = preferences.haptics, largeFileMb = preferences.largeFileMb,
                    scheduleEnabled = preferences.scheduleEnabled, scheduleFrequency = preferences.scheduleFrequency,
                    scheduleRequireCharging = preferences.scheduleRequireCharging,
                    scheduleRequireIdle = preferences.scheduleRequireIdle,
                    scheduleScanOnly = preferences.scheduleScanOnly,
                    scheduleStorageThreshold = preferences.scheduleStorageThreshold,
                    trashRetentionDays = preferences.trashRetentionDays, trashMaxMb = preferences.trashMaxMb,
                    message = if (imported != null) "已导入 ${imported.rules} 条规则和 ${imported.whitelist} 个保护项" else "导入失败：${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    fun updateSignedRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBusy = true, operationTitle = "正在校验规则更新", message = null) }
            val result = runCatching { ruleUpdateRepository.update() }
            _state.update { it.copy(isBusy = false, ruleUpdateInfo = ruleUpdateRepository.info(), message = result.fold({ info -> "规则已更新：v${info.version} · ${info.ruleCount} 条" }, { error -> "规则更新失败：${error.message}" })) }
        }
    }

    fun addSafRoot(uri: Uri) {
        val root = safRepository.add(uri)
        _state.update { it.copy(safRoots = safRepository.roots(), message = if (root != null) "已连接 ${root.name}" else "无法读取所选目录") }
    }

    fun removeSafRoot(uri: String) {
        safRepository.remove(uri)
        _state.update { it.copy(safRoots = safRepository.roots(), message = "已断开外部目录") }
    }

    fun analyzeSafRoots() {
        if (_state.value.safRoots.isEmpty()) return postMessage("请先连接一个外部目录")
        activeJob?.cancel()
        _state.update { it.copy(isBusy = true, operationTitle = "正在分析外部存储", progress = ScanProgress(), safAnalysis = null, message = null) }
        activeJob = viewModelScope.launch {
            try {
                val analysis = withContext(Dispatchers.IO) {
                    safRepository.analyze { progress -> _state.update { it.copy(progress = progress) } }
                }
                _state.update { it.copy(isBusy = false, safAnalysis = analysis, message = "外部存储分析完成") }
            } catch (_: CancellationException) {
                _state.update { it.copy(isBusy = false, message = "分析已停止") }
            } catch (error: Throwable) {
                _state.update { it.copy(isBusy = false, message = "外部存储分析失败：${error.message}") }
            }
        }
    }

    fun loadApps() {
        if (_state.value.appsLoading) return
        _state.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { appRepository.installedApps() } }
            _state.update {
                it.copy(
                    appsLoading = false,
                    apps = result.getOrDefault(emptyList()),
                    message = result.exceptionOrNull()?.let { error -> "读取应用列表失败：${error.message}" },
                )
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        preferences.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDeleteMode(mode: DeleteMode) {
        preferences.deleteMode = mode
        _state.update { it.copy(deleteMode = mode) }
    }

    fun setHaptics(enabled: Boolean) {
        preferences.haptics = enabled
        _state.update { it.copy(haptics = enabled) }
    }

    fun setLargeFileMb(value: Int) {
        preferences.largeFileMb = value
        _state.update { it.copy(largeFileMb = preferences.largeFileMb) }
    }

    fun setSchedule(enabled: Boolean, frequency: ScheduleFrequency = _state.value.scheduleFrequency) {
        preferences.scheduleEnabled = enabled
        preferences.scheduleFrequency = frequency
        ScheduleManager.configure(getApplication(), enabled, frequency, preferences.scheduleRequireCharging, preferences.scheduleRequireIdle)
        _state.update { it.copy(scheduleEnabled = enabled, scheduleFrequency = frequency) }
    }

    fun setAutoRules(
        requireCharging: Boolean? = null,
        requireIdle: Boolean? = null,
        scanOnly: Boolean? = null,
        storageThreshold: Int? = null,
    ) {
        requireCharging?.let { preferences.scheduleRequireCharging = it }
        requireIdle?.let { preferences.scheduleRequireIdle = it }
        scanOnly?.let { preferences.scheduleScanOnly = it }
        storageThreshold?.let { preferences.scheduleStorageThreshold = it }
        if (preferences.scheduleEnabled) {
            ScheduleManager.configure(getApplication(), true, preferences.scheduleFrequency, preferences.scheduleRequireCharging, preferences.scheduleRequireIdle)
        }
        _state.update {
            it.copy(
                scheduleRequireCharging = preferences.scheduleRequireCharging,
                scheduleRequireIdle = preferences.scheduleRequireIdle,
                scheduleScanOnly = preferences.scheduleScanOnly,
                scheduleStorageThreshold = preferences.scheduleStorageThreshold,
            )
        }
    }

    fun setTrashRules(retentionDays: Int? = null, maxMb: Int? = null) {
        retentionDays?.let { preferences.trashRetentionDays = it }
        maxMb?.let { preferences.trashMaxMb = it }
        val pruned = trashManager.prune(preferences.trashRetentionDays, preferences.trashMaxMb * 1024L * 1024L)
        _state.update {
            it.copy(
                trashRetentionDays = preferences.trashRetentionDays,
                trashMaxMb = preferences.trashMaxMb,
                trash = trashManager.list(),
                message = if (pruned.deleted > 0) "回收站已自动释放 ${formatBytes(pruned.releasedBytes)}" else it.message,
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun postMessage(message: String) = _state.update { it.copy(message = message) }

    private fun requireStorageAccess(): Boolean {
        val granted = StorageAccess.hasAllFilesAccess()
        if (!granted) postMessage("请先授予“所有文件访问”权限")
        _state.update { it.copy(hasStorageAccess = granted) }
        return granted
    }
}
