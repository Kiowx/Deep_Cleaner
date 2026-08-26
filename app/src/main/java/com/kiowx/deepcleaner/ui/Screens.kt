package com.kiowx.deepcleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiowx.deepcleaner.DeepCleanerUiState
import com.kiowx.deepcleaner.core.AppEntry
import com.kiowx.deepcleaner.core.CleanCategory
import com.kiowx.deepcleaner.core.CleanItem
import com.kiowx.deepcleaner.core.DeleteMode
import com.kiowx.deepcleaner.core.ScheduleFrequency
import com.kiowx.deepcleaner.core.ThemeMode
import com.kiowx.deepcleaner.core.ToolKind
import com.kiowx.deepcleaner.core.TrashRecord
import com.kiowx.deepcleaner.core.formatBytes
import com.kiowx.deepcleaner.ui.theme.AquaBlue
import com.kiowx.deepcleaner.ui.theme.BrightMint
import com.kiowx.deepcleaner.ui.theme.DeepNavy
import com.kiowx.deepcleaner.ui.theme.DeepTeal
import com.kiowx.deepcleaner.ui.theme.WarmOrange
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onGrantAccess: () -> Unit,
    onQuickScan: () -> Unit,
    onOpenTool: (ToolKind) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BrandHeader("Deep Cleaner") }
        item {
            Card(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(DeepNavy, Color(0xFF174EA6), DeepTeal)))
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("存储空间", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .72f))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${(state.storage.usedFraction * 100).toInt()}% 已用",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("可用", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
                            Text(formatBytes(state.storage.available), color = Color.White, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    LinearProgressIndicator(
                        progress = { state.storage.usedFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = BrightMint,
                        trackColor = Color.White.copy(alpha = .18f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("已用 ${formatBytes(state.storage.used)}", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text("总计 ${formatBytes(state.storage.total)}", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onQuickScan,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrightMint, contentColor = DeepNavy),
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null)
                        Spacer(Modifier.width(8.dp))
                        Text("立即扫描", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (!state.hasStorageAccess) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { PermissionBanner(false, onGrantAccess) } }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionLabel("快捷工具")
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactTool(Icons.Rounded.Storage, "大文件", AquaBlue, Modifier.weight(1f)) { onOpenTool(ToolKind.LARGE_FILES) }
                    CompactTool(Icons.Rounded.ContentCopy, "重复文件", Color(0xFF4F7FDB), Modifier.weight(1f)) { onOpenTool(ToolKind.DUPLICATES) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactTool(Icons.Rounded.FolderOff, "空文件夹", WarmOrange, Modifier.weight(1f)) { onOpenTool(ToolKind.EMPTY_FOLDERS) }
                    CompactTool(Icons.Rounded.Download, "下载管理", Color(0xFF4C89E8), Modifier.weight(1f)) { onOpenTool(ToolKind.DOWNLOADS) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionLabel("设备状态")
                Spacer(Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        StatusItem(
                            icon = Icons.Rounded.FolderOpen,
                            label = "权限",
                            value = if (state.hasStorageAccess) "已开启" else "未开启",
                            tint = DeepTeal,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.hasStorageAccess) onOpenSettings else onGrantAccess,
                        )
                        StatusItem(
                            icon = if (state.deleteMode == DeleteMode.TRASH) Icons.Rounded.Restore else Icons.Rounded.DeleteForever,
                            label = "回收站",
                            value = if (state.deleteMode == DeleteMode.TRASH) "已开启" else "已关闭",
                            tint = WarmOrange,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenSettings,
                        )
                        StatusItem(
                            icon = Icons.Rounded.Schedule,
                            label = "自动清理",
                            value = if (state.scheduleEnabled) "已开启" else "已关闭",
                            tint = Color(0xFF4C89E8),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }
        if (state.lastCleanedAt > 0) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("最近清理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("释放 ${formatBytes(state.lastCleanedBytes)}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastCleanedAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTool(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(11.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CleanerScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onGrantAccess: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectCategory: (String, Boolean) -> Unit,
    onClean: () -> Unit,
) {
    var confirmClean by remember { mutableStateOf(false) }
    if (confirmClean) {
        ConfirmCleanDialog(state, onDismiss = { confirmClean = false }) {
            confirmClean = false
            onClean()
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BrandHeader("智能清理") }
        if (!state.hasStorageAccess) item { PermissionBanner(false, onGrantAccess) }
        if (state.isBusy) {
            item { BusyPanel(state.operationTitle, state.progress.currentPath, state.progress.scannedFiles, state.progress.foundItems, onCancel) }
        } else if (state.items.isEmpty()) {
            item { EmptyScanCard(Icons.Rounded.CleaningServices, "开始深度扫描", "扫描后由你确认清理项", "开始扫描", onScan) }
        } else {
            item {
                ResultsHeader(
                    count = state.items.size,
                    bytes = state.items.sumOf(CleanItem::size),
                    selectedCount = state.selectedItems.size,
                    selectedBytes = state.selectedBytes,
                    allSelected = state.items.all(CleanItem::selected),
                    onSelectAll = onSelectAll,
                )
            }
            state.items.groupBy(CleanItem::category).forEach { (category, categoryItems) ->
                item(key = category.name) {
                    CategoryCard(category, categoryItems, onToggle, onSelectCategory)
                }
            }
            item {
                Button(
                    onClick = { confirmClean = true },
                    enabled = state.selectedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(if (state.deleteMode == DeleteMode.TRASH) Icons.Rounded.DeleteSweep else Icons.Rounded.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("清理 ${state.selectedItems.size} 项 · ${formatBytes(state.selectedBytes)}")
                }
            }
            item { TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("重新扫描") } }
        }
    }
}

@Composable
private fun ResultsHeader(count: Int, bytes: Long, selectedCount: Int, selectedBytes: Long, allSelected: Boolean, onSelectAll: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("扫描完成", style = MaterialTheme.typography.titleLarge)
                Text("发现 $count 项 · ${formatBytes(bytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已选 $selectedCount 项 · ${formatBytes(selectedBytes)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            FilterChip(selected = allSelected, onClick = { onSelectAll(!allSelected) }, label = { Text(if (allSelected) "取消全选" else "全选") })
        }
    }
}

@Composable
private fun CategoryCard(
    category: CleanCategory,
    categoryItems: List<CleanItem>,
    onToggle: (String) -> Unit,
    onSelectCategory: (String, Boolean) -> Unit,
) {
    val selected = categoryItems.count(CleanItem::selected)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable { onSelectCategory(category.name, selected != categoryItems.size) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected == categoryItems.size, onCheckedChange = { onSelectCategory(category.name, it) })
            Column(Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Text("${categoryItems.size} 项 · ${formatBytes(categoryItems.sumOf(CleanItem::size))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$selected/${categoryItems.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        categoryItems.take(20).forEachIndexed { index, item ->
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            CleanItemRow(item) { onToggle(item.id) }
        }
        if (categoryItems.size > 20) {
            Text("另有 ${categoryItems.size - 20} 项已折叠", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ConfirmCleanDialog(state: DeepCleanerUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Security, null) },
        title = { Text(if (state.deleteMode == DeleteMode.TRASH) "移入 Deep Cleaner 回收站？" else "永久删除所选项目？") },
        text = {
            Text(
                if (state.deleteMode == DeleteMode.TRASH) "将处理 ${state.selectedItems.size} 项（${formatBytes(state.selectedBytes)}）。清理后仍可从工具箱的回收站恢复。"
                else "将永久删除 ${state.selectedItems.size} 项（${formatBytes(state.selectedBytes)}），此操作不可恢复。删除前会再次核对路径、大小和文件状态。",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(if (state.deleteMode == DeleteMode.TRASH) "确认清理" else "永久删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun EmptyScanCard(icon: ImageVector, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick) { Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(7.dp)); Text(action) }
        }
    }
}

@Composable
fun ToolsScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onOpen: (ToolKind) -> Unit,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onClean: () -> Unit,
    onLargeSize: (Int) -> Unit,
    onRestoreTrash: (TrashRecord) -> Unit,
    onDeleteTrash: (TrashRecord) -> Unit,
    onEmptyTrash: () -> Unit,
    onOptimizeMedia: () -> Unit,
    onArchiveDownloads: () -> Unit,
) {
    val active = state.activeTool
    if (active == null) {
        ToolsOverview(modifier, state.deleteMode == DeleteMode.TRASH, onOpen)
        return
    }
    ToolDetailScreen(
        state, active, modifier, onBack, onRun, onCancel, onToggle, onSelectAll, onClean,
        onLargeSize, onRestoreTrash, onDeleteTrash, onEmptyTrash, onOptimizeMedia, onArchiveDownloads,
    )
}

@Composable
private fun ToolsOverview(modifier: Modifier, showTrash: Boolean, onOpen: (ToolKind) -> Unit) {
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BrandHeader("工具") }
        item { ToolCard(Icons.Rounded.PieChart, ToolKind.STORAGE_ANALYZER.title, ToolKind.STORAGE_ANALYZER.subtitle, Color(0xFF2563EB)) { onOpen(ToolKind.STORAGE_ANALYZER) } }
        item { ToolCard(Icons.Rounded.ImageSearch, ToolKind.SIMILAR_MEDIA.title, ToolKind.SIMILAR_MEDIA.subtitle, Color(0xFF7C3AED)) { onOpen(ToolKind.SIMILAR_MEDIA) } }
        item { ToolCard(Icons.Rounded.Storage, ToolKind.LARGE_FILES.title, ToolKind.LARGE_FILES.subtitle, AquaBlue) { onOpen(ToolKind.LARGE_FILES) } }
        item { ToolCard(Icons.Rounded.ContentCopy, ToolKind.DUPLICATES.title, ToolKind.DUPLICATES.subtitle, Color(0xFF4F7FDB)) { onOpen(ToolKind.DUPLICATES) } }
        item { ToolCard(Icons.Rounded.FolderOff, ToolKind.EMPTY_FOLDERS.title, ToolKind.EMPTY_FOLDERS.subtitle, WarmOrange) { onOpen(ToolKind.EMPTY_FOLDERS) } }
        item { ToolCard(Icons.Rounded.Download, ToolKind.DOWNLOADS.title, ToolKind.DOWNLOADS.subtitle, Color(0xFF4C89E8)) { onOpen(ToolKind.DOWNLOADS) } }
        item { ToolCard(Icons.Rounded.Compress, ToolKind.MEDIA_OPTIMIZER.title, ToolKind.MEDIA_OPTIMIZER.subtitle, Color(0xFF0891B2)) { onOpen(ToolKind.MEDIA_OPTIMIZER) } }
        item { ToolCard(Icons.Rounded.Android, ToolKind.APK_MANAGER.title, ToolKind.APK_MANAGER.subtitle, Color(0xFF16A34A)) { onOpen(ToolKind.APK_MANAGER) } }
        item { ToolCard(Icons.Rounded.Policy, ToolKind.PRIVACY_SCAN.title, ToolKind.PRIVACY_SCAN.subtitle, Color(0xFFDC2626)) { onOpen(ToolKind.PRIVACY_SCAN) } }
        item { ToolCard(Icons.Rounded.Shield, ToolKind.WHITELIST.title, ToolKind.WHITELIST.subtitle, Color(0xFF0F766E)) { onOpen(ToolKind.WHITELIST) } }
        item { ToolCard(Icons.Rounded.History, ToolKind.HISTORY.title, ToolKind.HISTORY.subtitle, Color(0xFF64748B)) { onOpen(ToolKind.HISTORY) } }
        item { ToolCard(Icons.Rounded.Usb, ToolKind.EXTERNAL_STORAGE.title, ToolKind.EXTERNAL_STORAGE.subtitle, Color(0xFF475569)) { onOpen(ToolKind.EXTERNAL_STORAGE) } }
        item { ToolCard(Icons.Rounded.Cached, ToolKind.SYSTEM_CACHE.title, ToolKind.SYSTEM_CACHE.subtitle, Color(0xFF0284C7)) { onOpen(ToolKind.SYSTEM_CACHE) } }
        if (showTrash) {
            item { ToolCard(Icons.Rounded.AutoDelete, ToolKind.TRASH.title, ToolKind.TRASH.subtitle, DeepTeal) { onOpen(ToolKind.TRASH) } }
        }
    }
}

@Composable
private fun ToolDetailScreen(
    state: DeepCleanerUiState,
    tool: ToolKind,
    modifier: Modifier,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onClean: () -> Unit,
    onLargeSize: (Int) -> Unit,
    onRestoreTrash: (TrashRecord) -> Unit,
    onDeleteTrash: (TrashRecord) -> Unit,
    onEmptyTrash: () -> Unit,
    onOptimizeMedia: () -> Unit,
    onArchiveDownloads: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) ConfirmCleanDialog(state, { confirm = false }) { confirm = false; onClean() }
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                Column {
                    Text(tool.title, style = MaterialTheme.typography.titleLarge)
                    Text(tool.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (tool == ToolKind.TRASH) {
            if (state.trash.isEmpty()) item { EmptyScanCard(Icons.Rounded.AutoDelete, "回收站是空的", "默认清理的文件会安全保存在这里", "刷新", onRun) }
            else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${state.trash.size} 个可恢复项目", style = MaterialTheme.typography.titleMedium)
                                Text("共 ${formatBytes(state.trash.sumOf(TrashRecord::size))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = onEmptyTrash) { Text("清空") }
                        }
                    }
                }
                items(state.trash, key = TrashRecord::id) { record -> TrashRow(record, onRestoreTrash, onDeleteTrash) }
            }
            return@LazyColumn
        }
        if (tool == ToolKind.LARGE_FILES && state.items.isEmpty() && !state.isBusy) {
            item {
                Column {
                    Text("最小文件大小", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 256, 512, 1024).forEach { mb ->
                            FilterChip(selected = state.largeFileMb == mb, onClick = { onLargeSize(mb) }, label = { Text(if (mb >= 1024) "1 GB" else "$mb MB") })
                        }
                    }
                }
            }
        }
        if (state.isBusy) item { BusyPanel(state.operationTitle, state.progress.currentPath, state.progress.scannedFiles, state.progress.foundItems, onCancel) }
        else if (state.items.isEmpty()) item { EmptyScanCard(toolIcon(tool), "准备扫描${tool.title}", tool.subtitle, "开始扫描", onRun) }
        else {
            item {
                ResultsHeader(
                    state.items.size, state.items.sumOf(CleanItem::size), state.selectedItems.size, state.selectedBytes,
                    state.items.all(CleanItem::selected), onSelectAll,
                )
            }
            items(state.items, key = CleanItem::id) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { CleanItemRow(item) { onToggle(item.id) } }
            }
            item {
                Button(
                    onClick = { if (tool == ToolKind.MEDIA_OPTIMIZER) onOptimizeMedia() else confirm = true },
                    enabled = state.selectedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(if (tool == ToolKind.MEDIA_OPTIMIZER) Icons.Rounded.Compress else Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (tool == ToolKind.MEDIA_OPTIMIZER) "压缩所选 ${state.selectedItems.size} 项" else "处理所选 ${state.selectedItems.size} 项")
                }
            }
            if (tool == ToolKind.DOWNLOADS) {
                item { OutlinedButton(onClick = onArchiveDownloads, enabled = state.selectedItems.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("归档到 Download/DeepCleanerArchive") } }
            }
            item { TextButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) { Text("重新扫描") } }
        }
    }
}

private fun toolIcon(tool: ToolKind): ImageVector = when (tool) {
    ToolKind.STORAGE_ANALYZER -> Icons.Rounded.PieChart
    ToolKind.SIMILAR_MEDIA -> Icons.Rounded.ImageSearch
    ToolKind.LARGE_FILES -> Icons.Rounded.Storage
    ToolKind.DUPLICATES -> Icons.Rounded.ContentCopy
    ToolKind.EMPTY_FOLDERS -> Icons.Rounded.FolderOff
    ToolKind.DOWNLOADS -> Icons.Rounded.Download
    ToolKind.MEDIA_OPTIMIZER -> Icons.Rounded.Compress
    ToolKind.APK_MANAGER -> Icons.Rounded.Android
    ToolKind.PRIVACY_SCAN -> Icons.Rounded.Policy
    ToolKind.WHITELIST -> Icons.Rounded.Shield
    ToolKind.HISTORY -> Icons.Rounded.History
    ToolKind.EXTERNAL_STORAGE -> Icons.Rounded.Usb
    ToolKind.SYSTEM_CACHE -> Icons.Rounded.Cached
    ToolKind.TRASH -> Icons.Rounded.AutoDelete
}

@Composable
private fun TrashRow(record: TrashRecord, onRestore: (TrashRecord) -> Unit, onDelete: (TrashRecord) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatBytes(record.size)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.deletedAt))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(record.originalPath, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, color = MaterialTheme.colorScheme.outline)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onRestore(record) }) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(5.dp)); Text("恢复") }
                TextButton(onClick = { onDelete(record) }) { Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(5.dp)); Text("永久删除") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onRefresh: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onManageSpace: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onGrantUsageAccess: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val filtered = state.apps.filter { app ->
        (query.isBlank() || app.name.contains(query, true) || app.packageName.contains(query, true)) &&
            when (filter) {
                1 -> !app.isSystem
                2 -> app.isSystem
                3 -> !app.isSystem && state.hasUsageAccess && (app.lastUsedAt == 0L || System.currentTimeMillis() - app.lastUsedAt >= 90L * 86_400_000L)
                else -> true
            }
    }
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BrandHeader("应用", trailing = { IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "刷新") } }) }
        if (!state.hasUsageAccess) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onGrantUsageAccess),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("启用应用深度分析", fontWeight = FontWeight.SemiBold)
                            Text("显示数据、缓存和最后使用时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索应用或包名") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清空") } },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "用户应用", "系统应用", "长期未用").forEachIndexed { index, label ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                }
            }
        }
        if (state.appsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!state.appsLoading && filtered.isEmpty()) item { EmptyScanCard(Icons.Rounded.Apps, "没有找到应用", "尝试修改搜索词或筛选条件", "刷新", onRefresh) }
        items(filtered, key = AppEntry::packageName) { app ->
            AppRow(app, onLaunch, onManageSpace, onUninstall)
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onLaunch: (AppEntry) -> Unit, onManageSpace: (AppEntry) -> Unit, onUninstall: (AppEntry) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                        RoundedCornerShape(15.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text(app.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${app.version.ifBlank { "未知版本" }} · APK ${formatBytes(app.apkSize)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(app.packageName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.dataBytes >= 0) {
                        Text(
                            "应用 ${formatBytes(app.appBytes.coerceAtLeast(0))} · 数据 ${formatBytes(app.dataBytes)} · 缓存 ${formatBytes(app.cacheBytes.coerceAtLeast(0))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    if (app.lastUsedAt > 0) {
                        Text("上次使用 ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(app.lastUsedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape = CircleShape, color = if (app.isSystem) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                    Text(if (app.isSystem) "系统" else "用户", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onLaunch(app) }) { Icon(Icons.Rounded.PlayArrow, null); Text("打开") }
                TextButton(onClick = { onManageSpace(app) }) { Icon(Icons.Rounded.SettingsSuggest, null); Text("空间") }
                if (!app.isSystem) TextButton(onClick = { onUninstall(app) }) { Icon(Icons.Rounded.SystemUpdateAlt, null); Text("卸载") }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onGrantAccess: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDeleteMode: (DeleteMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onLargeSize: (Int) -> Unit,
    onSchedule: (Boolean, ScheduleFrequency) -> Unit,
    onAutoCharging: (Boolean) -> Unit,
    onAutoIdle: (Boolean) -> Unit,
    onAutoScanOnly: (Boolean) -> Unit,
    onAutoStorageThreshold: (Int) -> Unit,
    onTrashRetention: (Int) -> Unit,
    onTrashMaxMb: (Int) -> Unit,
    onJoinQqGroup: () -> Unit,
    onOpenAuthor: () -> Unit,
) {
    var showThemeChoices by remember { mutableStateOf(false) }
    var showDeleteChoices by remember { mutableStateOf(false) }
    var showLargeFileChoices by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val themeValues = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val deleteLabels = listOf("永久删除（默认）", "可恢复删除")
    val deleteValues = listOf(DeleteMode.PERMANENT, DeleteMode.TRASH)
    val largeFileValues = listOf(100, 256, 512, 1024)
    val largeFileLabels = largeFileValues.map { if (it == 1024) "1 GB" else "$it MB" }

    if (showThemeChoices) {
        SettingsChoiceDialog(
            title = "主题",
            options = themeLabels,
            selectedIndex = themeValues.indexOf(state.themeMode),
            onDismiss = { showThemeChoices = false },
        ) { index ->
            onTheme(themeValues[index])
            showThemeChoices = false
        }
    }
    if (showDeleteChoices) {
        SettingsChoiceDialog(
            title = "删除方式",
            options = deleteLabels,
            selectedIndex = deleteValues.indexOf(state.deleteMode),
            onDismiss = { showDeleteChoices = false },
        ) { index ->
            onDeleteMode(deleteValues[index])
            showDeleteChoices = false
        }
    }
    if (showLargeFileChoices) {
        SettingsChoiceDialog(
            title = "大文件阈值",
            options = largeFileLabels,
            selectedIndex = largeFileValues.indexOf(state.largeFileMb),
            onDismiss = { showLargeFileChoices = false },
        ) { index ->
            onLargeSize(largeFileValues[index])
            showLargeFileChoices = false
        }
    }

    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BrandHeader("设置") }
        if (!state.hasStorageAccess) item { PermissionBanner(false, onGrantAccess) }
        item {
            SettingsGroup {
                SettingsValueRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "主题",
                    value = themeLabels[themeValues.indexOf(state.themeMode).coerceAtLeast(0)],
                    onClick = { showThemeChoices = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                SettingsValueRow(
                    icon = if (state.deleteMode == DeleteMode.PERMANENT) Icons.Rounded.DeleteForever else Icons.Rounded.Restore,
                    title = "删除方式",
                    value = if (state.deleteMode == DeleteMode.PERMANENT) "永久删除" else "可恢复删除",
                    onClick = { showDeleteChoices = true },
                )
            }
        }
        item {
            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(13.dp))
                    Text("高级设置", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (advancedExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (advancedExpanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    SettingsToggleRow(Icons.Rounded.PhoneAndroid, "触感反馈", state.haptics, onHaptics)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    SettingsValueRow(
                        icon = Icons.Rounded.Storage,
                        title = "大文件阈值",
                        value = if (state.largeFileMb == 1024) "1 GB" else "${state.largeFileMb} MB",
                        onClick = { showLargeFileChoices = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    SettingsToggleRow(Icons.Rounded.Schedule, "自动清理", state.scheduleEnabled) {
                        onSchedule(it, state.scheduleFrequency)
                    }
                    if (state.scheduleEnabled) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 37.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ScheduleFrequency.entries.forEach { frequency ->
                                FilterChip(
                                    selected = state.scheduleFrequency == frequency,
                                    onClick = { onSchedule(true, frequency) },
                                    label = { Text(if (frequency == ScheduleFrequency.DAILY) "每天" else "每周") },
                                )
                            }
                        }
                        SettingsToggleRow(Icons.Rounded.PhoneAndroid, "仅充电时运行", state.scheduleRequireCharging, onAutoCharging)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                        SettingsToggleRow(Icons.Rounded.Schedule, "仅设备空闲时运行", state.scheduleRequireIdle, onAutoIdle)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                        SettingsToggleRow(Icons.Rounded.Security, "仅扫描，不自动删除", state.scheduleScanOnly, onAutoScanOnly)
                        Text("存储使用达到阈值后运行", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 36.dp, top = 6.dp))
                        Row(Modifier.padding(start = 36.dp, bottom = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(75, 85, 90, 95).forEach { percent ->
                                FilterChip(selected = state.scheduleStorageThreshold == percent, onClick = { onAutoStorageThreshold(percent) }, label = { Text("$percent%") })
                            }
                        }
                    }
                }
            }
        }
        if (state.deleteMode == DeleteMode.TRASH) {
            item {
                SettingsGroup {
                    Text("回收站规则", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Text("自动保留期限", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(7, 14, 30, 60).forEach { days ->
                            FilterChip(selected = state.trashRetentionDays == days, onClick = { onTrashRetention(days) }, label = { Text("$days 天") })
                        }
                    }
                    Text("容量上限", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        listOf(512, 1024, 2048, 4096).forEach { mb ->
                            FilterChip(selected = state.trashMaxMb == mb, onClick = { onTrashMaxMb(mb) }, label = { Text(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB") })
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup {
                SettingsValueRow(
                    icon = Icons.Rounded.Groups,
                    title = "QQ 交流群",
                    value = "670804369",
                    onClick = onJoinQqGroup,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                SettingsValueRow(
                    icon = Icons.Rounded.Person,
                    title = "作者",
                    value = "Kio",
                    onClick = onOpenAuthor,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(11.dp))
                Text("Deep Cleaner", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("1.0.0 · Android 8–16", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

@Composable
private fun SettingsValueRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(13.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsToggleRow(icon: ImageVector, title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(13.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelected(index) }.padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (index == selectedIndex) {
                            Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
