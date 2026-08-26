package com.kiowx.deepcleaner.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kiowx.deepcleaner.DeepCleanerUiState
import com.kiowx.deepcleaner.DeepCleanerViewModel
import com.kiowx.deepcleaner.core.AppEntry
import com.kiowx.deepcleaner.core.MainSection
import com.kiowx.deepcleaner.core.ToolKind

@Composable
fun DeepCleanerRoot(state: DeepCleanerUiState, viewModel: DeepCleanerViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissionAndStorage() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addSafRoot)
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
            runCatching { context.startActivity(appIntent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyStorageLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            )
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestUsageAccess() {
        runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    fun openSystemCache() {
        val intent = if (Build.VERSION.SDK_INT >= 30) Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        else Intent(StorageManager.ACTION_MANAGE_STORAGE)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE)) }
                .onFailure { viewModel.postMessage("无法打开系统存储管理") }
        }
    }

    fun openTool(tool: ToolKind) {
        if (tool == ToolKind.SYSTEM_CACHE) openSystemCache() else viewModel.openTool(tool)
    }

    fun joinQqGroup() {
        val groupNumber = "670804369"
        val intent = Intent(
            Intent.ACTION_VIEW,
            "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupNumber&card_type=group&source=qrcode".toUri(),
        )
        runCatching { context.startActivity(intent) }.onFailure {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Deep Cleaner QQ 群", groupNumber))
            viewModel.postMessage("未检测到 QQ，群号 $groupNumber 已复制")
        }
    }

    fun openAuthorPage() {
        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/Kiowx".toUri())
        runCatching { context.startActivity(intent) }
            .onFailure { viewModel.postMessage("无法打开作者主页") }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = state.activeTool != null || state.section != MainSection.HOME) {
        if (state.activeTool != null) viewModel.closeTool() else viewModel.selectSection(MainSection.HOME)
    }

    val navigationItems = listOf(
        Triple(MainSection.HOME, "首页", Icons.Rounded.Home),
        Triple(MainSection.TOOLS, "工具", Icons.Rounded.GridView),
        Triple(MainSection.APPS, "应用", Icons.Rounded.Apps),
        Triple(MainSection.SETTINGS, "设置", Icons.Rounded.Settings),
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val expanded = maxWidth >= 840.dp
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                navigationItems.forEach { (section, label, icon) ->
                    NavigationBarItem(
                        selected = state.section == section,
                        onClick = { viewModel.selectSection(section) },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        if (expanded) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                navigationItems.forEach { (section, label, icon) ->
                    NavigationRailItem(
                        selected = state.section == section,
                        onClick = { viewModel.selectSection(section) },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        }
        val modifier = Modifier.fillMaxSize().weight(1f)
        when (state.section) {
            MainSection.HOME -> HomeScreen(
                state = state,
                modifier = modifier,
                onGrantAccess = ::requestStorageAccess,
                onQuickScan = { viewModel.selectSection(MainSection.CLEAN); viewModel.runQuickScan() },
                onOpenTool = ::openTool,
                onOpenSettings = { viewModel.selectSection(MainSection.SETTINGS) },
            )
            MainSection.CLEAN -> CleanerScreen(
                state = state,
                modifier = modifier,
                onGrantAccess = ::requestStorageAccess,
                onScan = viewModel::runQuickScan,
                onCancel = viewModel::cancelOperation,
                onToggle = viewModel::toggleItem,
                onSelectAll = viewModel::selectAll,
                onSelectCategory = viewModel::selectCategory,
                onClean = viewModel::cleanSelected,
            )
            MainSection.TOOLS -> when (state.activeTool) {
                ToolKind.STORAGE_ANALYZER -> StorageAnalysisScreen(state, modifier, viewModel::closeTool, viewModel::runActiveTool, viewModel::cancelOperation)
                ToolKind.WHITELIST -> WhitelistScreen(state, modifier, viewModel::closeTool, viewModel::addWhitelist, viewModel::removeWhitelist)
                ToolKind.HISTORY -> HistoryScreen(state, modifier, viewModel::closeTool, viewModel::clearHistory) { viewModel.openTool(ToolKind.TRASH) }
                ToolKind.EXTERNAL_STORAGE -> ExternalStorageScreen(
                    state, modifier, viewModel::closeTool, { treeLauncher.launch(null) }, viewModel::removeSafRoot,
                    viewModel::analyzeSafRoots, viewModel::cancelOperation,
                )
                else -> ToolsScreen(
                    state = state,
                    modifier = modifier,
                    onOpen = ::openTool,
                    onBack = viewModel::closeTool,
                    onRun = viewModel::runActiveTool,
                    onCancel = viewModel::cancelOperation,
                    onToggle = viewModel::toggleItem,
                    onSelectAll = viewModel::selectAll,
                    onClean = viewModel::cleanSelected,
                    onLargeSize = viewModel::setLargeFileMb,
                    onRestoreTrash = viewModel::restoreTrash,
                    onDeleteTrash = viewModel::deleteTrash,
                    onEmptyTrash = viewModel::emptyTrash,
                    onOptimizeMedia = viewModel::optimizeSelectedMedia,
                    onArchiveDownloads = viewModel::archiveSelectedDownloads,
                )
            }
            MainSection.APPS -> AppsScreen(
                state = state,
                modifier = modifier,
                onRefresh = viewModel::loadApps,
                onLaunch = { app -> launchApp(context, app, viewModel) },
                onManageSpace = { app -> manageAppSpace(context, app, viewModel) },
                onUninstall = { app -> uninstallApp(context, app, viewModel) },
                onGrantUsageAccess = ::requestUsageAccess,
            )
            MainSection.SETTINGS -> SettingsScreen(
                state = state,
                modifier = modifier,
                onGrantAccess = ::requestStorageAccess,
                onTheme = viewModel::setTheme,
                onDeleteMode = viewModel::setDeleteMode,
                onHaptics = viewModel::setHaptics,
                onLargeSize = viewModel::setLargeFileMb,
                onSchedule = { enabled, frequency ->
                    viewModel.setSchedule(enabled, frequency)
                    if (enabled) requestNotifications()
                },
                onAutoCharging = { viewModel.setAutoRules(requireCharging = it) },
                onAutoIdle = { viewModel.setAutoRules(requireIdle = it) },
                onAutoScanOnly = { viewModel.setAutoRules(scanOnly = it) },
                onAutoStorageThreshold = { viewModel.setAutoRules(storageThreshold = it) },
                onTrashRetention = { viewModel.setTrashRules(retentionDays = it) },
                onTrashMaxMb = { viewModel.setTrashRules(maxMb = it) },
                onJoinQqGroup = ::joinQqGroup,
                onOpenAuthor = ::openAuthorPage,
            )
        }
        }
    }
    }
}

private fun launchApp(context: Context, app: AppEntry, viewModel: DeepCleanerViewModel) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent != null) context.startActivity(intent) else viewModel.postMessage("该应用没有可启动界面")
}

private fun uninstallApp(context: Context, app: AppEntry, viewModel: DeepCleanerViewModel) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()))
    }.onFailure { viewModel.postMessage("无法打开系统卸载界面") }
}

private fun manageAppSpace(context: Context, app: AppEntry, viewModel: DeepCleanerViewModel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Environment.isExternalStorageManager()) {
        val storage = context.getSystemService(StorageManager::class.java)
        val pending = runCatching { storage.getManageSpaceActivityIntent(app.packageName, 7101) }.getOrNull()
        if (pending != null) {
            runCatching { pending.send() }.onFailure { viewModel.postMessage("无法打开应用的空间管理页面") }
            return
        }
    }
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        viewModel.postMessage("无法打开应用设置")
    }
}
