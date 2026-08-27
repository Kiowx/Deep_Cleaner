package com.kiowx.deepcleaner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiowx.deepcleaner.DeepCleanerUiState
import com.kiowx.deepcleaner.core.CleanProfile
import com.kiowx.deepcleaner.core.CustomCleanRule
import com.kiowx.deepcleaner.core.VaultEntry
import com.kiowx.deepcleaner.core.formatBytes
import java.text.DateFormat
import java.util.Date

@Composable
fun CustomRulesScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: (String, String, String, Int, Int, Boolean) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onToggleItem: (String) -> Unit,
    onClean: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var minimumMb by remember { mutableStateOf("0") }
    var olderDays by remember { mutableStateOf("0") }
    var safe by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AdvancedHeader("自定义规则", "按路径、扩展名、大小和时间匹配", onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("规则名称") }, singleLine = true)
                    OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("路径包含（可选）") }, placeholder = { Text("例如 Download") }, singleLine = true)
                    OutlinedTextField(extensions, { extensions = it }, Modifier.fillMaxWidth(), label = { Text("扩展名，逗号分隔（可选）") }, placeholder = { Text("tmp, log, bak") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(minimumMb, { minimumMb = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("最小 MB") }, singleLine = true)
                        OutlinedTextField(olderDays, { olderDays = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("早于天数") }, singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(safe, { safe = it })
                        Text("默认勾选（仅建议用于确认安全的缓存规则）")
                    }
                    Button(
                        onClick = {
                            onAdd(name, path, extensions, minimumMb.toIntOrNull() ?: 0, olderDays.toIntOrNull() ?: 0, safe)
                            name = ""; path = ""; extensions = ""; minimumMb = "0"; olderDays = "0"; safe = false
                        },
                        enabled = name.isNotBlank() && (path.isNotBlank() || extensions.isNotBlank()),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("添加规则") }
                }
            }
        }
        items(state.customRules, key = CustomCleanRule::id) { rule ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                rule.pathContains.takeIf(String::isNotBlank)?.let { "路径 $it" },
                                rule.extensions.takeIf(Set<String>::isNotEmpty)?.joinToString(prefix = "类型 "),
                                rule.minimumBytes.takeIf { it > 0 }?.let { "≥ ${formatBytes(it)}" },
                                rule.olderThanDays.takeIf { it > 0 }?.let { "早于 $it 天" },
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(rule.enabled, { onEnabled(rule.id, it) })
                    IconButton(onClick = { onRemove(rule.id) }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                }
            }
        }
        item {
            Button(onClick = onRun, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("执行本地与签名规则")
            }
        }
        if (state.isBusy) item { BusyPanel(state.operationTitle, state.progress.currentPath, state.progress.scannedFiles, state.progress.foundItems, onCancel) }
        if (state.items.isNotEmpty()) {
            item { Text("匹配结果 ${state.items.size} 项 · ${formatBytes(state.items.sumOf { it.size })}", style = MaterialTheme.typography.titleMedium) }
            items(state.items.take(200), key = { "rule-${it.id}" }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { CleanItemRow(item) { onToggleItem(item.id) } }
            }
            item { Button(onClick = onClean, enabled = state.selectedItems.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("处理已选 ${state.selectedItems.size} 项") } }
        }
    }
}

@Composable
fun CleanProfilesScreen(state: DeepCleanerUiState, modifier: Modifier, onBack: () -> Unit, onSelect: (CleanProfile) -> Unit) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdvancedHeader("清理方案", "选择首页“立即扫描”使用的策略", onBack) }
        items(CleanProfile.entries, key = CleanProfile::name) { profile ->
            Card(colors = CardDefaults.cardColors(containerColor = if (state.cleanProfile == profile) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.title, style = MaterialTheme.typography.titleMedium)
                        Text(profile.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(selected = state.cleanProfile == profile, onClick = { onSelect(profile) }, label = { Text(if (state.cleanProfile == profile) "当前" else "使用") })
                }
            }
        }
    }
}

@Composable
fun StorageTrendsScreen(state: DeepCleanerUiState, modifier: Modifier, onBack: () -> Unit, onClear: () -> Unit) {
    val points = state.storageTrends
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AdvancedHeader("存储趋势", "最多保留 90 个本地空间快照", onBack) }
        if (points.isEmpty()) item { Text("暂无趋势数据，后续启动、扫描和清理时会自动记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else {
            item {
                val color = MaterialTheme.colorScheme.primary
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        val delta = points.last().usedBytes - points.first().usedBytes
                        Text("当前已用 ${formatBytes(points.last().usedBytes)}", style = MaterialTheme.typography.titleMedium)
                        Text("期间变化 ${if (delta >= 0) "+" else ""}${formatBytes(kotlin.math.abs(delta))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val min = points.minOf { it.usedBytes }
                            val max = points.maxOf { it.usedBytes }
                            val range = (max - min).coerceAtLeast(1)
                            val path = Path()
                            points.forEachIndexed { index, point ->
                                val x = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1)
                                val y = size.height - size.height * (point.usedBytes - min).toFloat() / range
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                drawCircle(color, 4.dp.toPx(), Offset(x, y))
                            }
                            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
            }
            items(points.asReversed().take(30), key = { it.timestamp }) { point ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(point.timestamp)), Modifier.weight(1f))
                    Text(formatBytes(point.usedBytes), color = MaterialTheme.colorScheme.primary)
                }
            }
            item { TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("重置趋势") } }
        }
    }
}

@Composable
fun VaultScreen(
    state: DeepCleanerUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onExport: (VaultEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { AdvancedHeader("文件保险箱", "AES-256 加密保存，敏感操作使用系统身份验证", onBack) }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(7.dp)); Text("验证身份并添加文件")
            }
        }
        if (state.vaultEntries.isEmpty()) item { Text("保险箱为空。原文件不会自动删除。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.vaultEntries, key = VaultEntry::id) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatBytes(entry.size)} · ${DateFormat.getDateInstance().format(Date(entry.addedAt))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onExport(entry) }) { Icon(Icons.Rounded.FileUpload, "验证并导出") }
                    IconButton(onClick = { onDelete(entry.id) }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                }
            }
        }
    }
}

@Composable
fun ConfigBackupScreen(modifier: Modifier, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AdvancedHeader("配置备份", "备份规则、保护名单和主要设置", onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(7.dp)); Text("导出 JSON 配置") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(7.dp)); Text("导入并覆盖当前配置") }
                    Text("不会导出保险箱文件、清理历史或任何用户文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RuleUpdateScreen(state: DeepCleanerUiState, modifier: Modifier, onBack: () -> Unit, onUpdate: () -> Unit) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AdvancedHeader("规则更新", "仅接受内置 RSA 公钥验证通过的规则", onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (state.ruleUpdateInfo.version > 0) "当前规则 v${state.ruleUpdateInfo.version}" else "尚未下载远程规则", style = MaterialTheme.typography.titleMedium)
                    Text("${state.ruleUpdateInfo.ruleCount} 条已验证规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onUpdate, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (state.isBusy) Icons.Rounded.Refresh else Icons.Rounded.CloudDownload, null)
                        Spacer(Modifier.width(7.dp)); Text(if (state.isBusy) "正在校验" else "检查并更新")
                    }
                }
            }
        }
    }
}
