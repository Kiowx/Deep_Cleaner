package com.kiowx.deepcleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiowx.deepcleaner.DeepCleanerUiState
import com.kiowx.deepcleaner.core.StorageBucket
import com.kiowx.deepcleaner.core.WhitelistType
import com.kiowx.deepcleaner.core.formatBytes
import java.text.DateFormat
import java.util.Date

@Composable
fun StorageAnalysisScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AdvancedHeader("空间分析", "分类统计与目录占用排行", onBack) }
        if (state.isBusy) item { BusyPanel(state.operationTitle, state.progress.currentPath, state.progress.scannedFiles, state.progress.foundItems, onCancel) }
        else if (state.storageAnalysis == null) {
            item { EmptyScanCard(Icons.Rounded.PieChart, "分析设备空间", "查看文件类型和目录占用", "开始分析", onRun) }
        } else {
            val analysis = state.storageAnalysis
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(formatBytes(analysis.scannedBytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("${analysis.scannedFiles} 个文件已纳入分析", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Text("文件类型", style = MaterialTheme.typography.titleMedium) }
            items(analysis.buckets, key = { it.category.name }) { bucket -> StorageBucketCard(bucket, analysis.scannedBytes) }
            item { Text("目录占用排行", style = MaterialTheme.typography.titleMedium) }
            items(analysis.directories, key = { it.path }) { directory ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(directory.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            Text("${directory.files} 个文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatBytes(directory.bytes), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { TextButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Text("重新分析") } }
        }
    }
}

@Composable
private fun StorageBucketCard(bucket: StorageBucket, total: Long) {
    val fraction = if (total <= 0) 0f else bucket.bytes.toFloat() / total
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row {
                Text(bucket.category.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${formatBytes(bucket.bytes)} · ${bucket.files} 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
        }
    }
}

@Composable
fun WhitelistScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: (WhitelistType, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var type by remember { mutableStateOf(WhitelistType.PATH) }
    var value by remember { mutableStateOf("") }
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AdvancedHeader("保护名单", "扫描和清理时自动跳过", onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        WhitelistType.entries.forEach { candidate ->
                            FilterChip(
                                selected = type == candidate,
                                onClick = { type = candidate },
                                label = { Text(when (candidate) { WhitelistType.PATH -> "路径"; WhitelistType.EXTENSION -> "扩展名"; WhitelistType.APP -> "应用" }) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(when (type) { WhitelistType.PATH -> "/storage/emulated/0/..."; WhitelistType.EXTENSION -> "例如 psd"; WhitelistType.APP -> "例如 com.example.app" }) },
                    )
                    Button(
                        onClick = { onAdd(type, value); value = "" },
                        enabled = value.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("添加保护项") }
                }
            }
        }
        if (state.whitelist.isEmpty()) item { Text("暂无保护项", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
        items(state.whitelist, key = { it.id }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.value, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(when (entry.type) { WhitelistType.PATH -> "路径"; WhitelistType.EXTENSION -> "扩展名"; WhitelistType.APP -> "应用包名" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(entry.id) }) { Icon(Icons.Rounded.DeleteOutline, "移除") }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AdvancedHeader("清理历史", "最近 200 次处理记录", onBack) }
        if (state.history.isEmpty()) item { EmptyScanCard(Icons.Rounded.History, "暂无清理历史", "完成清理后会显示在这里", "返回", onBack) }
        else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("累计释放 ${formatBytes(state.history.sumOf { it.releasedBytes })}", style = MaterialTheme.typography.titleMedium)
                            Text("${state.history.sumOf { it.deleted }} 个项目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onClear) { Text("清空记录") }
                    }
                }
            }
            if (state.trash.isNotEmpty()) {
                item { Button(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) { Text("打开回收站撤销清理（${state.trash.size} 项）") } }
            }
            items(state.history, key = { it.id }) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.source, fontWeight = FontWeight.SemiBold)
                            Text("处理 ${record.deleted} 项${if (record.failed > 0) " · ${record.failed} 项失败" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (record.categories.isNotBlank()) Text(record.categories, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            if (record.durationMs > 0) Text("耗时 ${record.durationMs / 1000.0} 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(formatBytes(record.releasedBytes), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ExternalStorageScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AdvancedHeader("外部存储", "SD 卡、U 盘和云端文档目录", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("连接目录")
                }
                Button(onClick = onAnalyze, enabled = state.safRoots.isNotEmpty() && !state.isBusy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PieChart, null); Spacer(Modifier.width(7.dp)); Text("分析空间")
                }
            }
        }
        if (state.isBusy) item { BusyPanel(state.operationTitle, state.progress.currentPath, state.progress.scannedFiles, state.progress.foundItems, onCancel) }
        if (state.safRoots.isEmpty()) item { Text("通过 Android 系统文件选择器授权目录后，Deep Cleaner 会保留访问权限。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        items(state.safRoots, key = { it.uri }) { root ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(root.name, fontWeight = FontWeight.SemiBold)
                        Text(root.uri, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(root.uri) }) { Icon(Icons.Rounded.DeleteOutline, "断开") }
                }
            }
        }
        state.safAnalysis?.let { analysis ->
            item { Text("已分析 ${analysis.scannedFiles} 个文件 · ${formatBytes(analysis.scannedBytes)}", style = MaterialTheme.typography.titleMedium) }
            items(analysis.buckets, key = { "saf-${it.category.name}" }) { bucket -> StorageBucketCard(bucket, analysis.scannedBytes) }
            items(analysis.directories, key = { "saf-${it.path}" }) { directory ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(directory.path, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(formatBytes(directory.bytes), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
