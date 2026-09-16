package dev.hk256.retimer.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.ui.components.ScreenHeadline
import java.time.Instant
import java.time.ZoneId

/** 预览列表的排序方式。 */
private enum class MediaSort(val label: String) {
    NAME("文件名"),
    ORIGINAL("原始时间"),
    TARGET("修改后时间"),
}

/**
 * 预览里的一行。
 *
 * 编辑页与修正页各自的行模型都映射成它，预览阶段本身才能只有一份实现。
 */
internal data class MediaPreviewRow(
    val id: String,
    val displayName: String,
    /** 文件名下面那行说明：候选来源、目标时间的来源、或者为什么没法处理。 */
    val reason: String?,
    val currentTime: Instant?,
    val targetTime: Instant?,
    val included: Boolean,
    val thumbnailUri: Uri?,
)

/**
 * 预览阶段：编辑页与修正页共用同一份实现。
 *
 * 页面只提供标题、主操作文案与行数据；统计口径、排序、列表样式、底部动作条都在这里统一，
 * 避免两个页面各长一套（术语也会跟着分叉）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPreviewStage(
    headlineIcon: ImageVector,
    headlineTitle: String,
    /** 统计行里"将处理多少项"的说法，两个页面各自用词（将修正 / 将修改）。 */
    primaryCountLabel: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    zoneId: ZoneId,
    rows: List<MediaPreviewRow>,
    onToggleRow: (String, Boolean) -> Unit,
    onEditRow: (String) -> Unit,
    onPreviewRow: (String) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    primaryEnabled: Boolean = true,
) {
    var sort by remember { mutableStateOf(MediaSort.TARGET) }
    var descending by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val ordered =
        remember(rows, sort, descending) {
            val comparator =
                when (sort) {
                    MediaSort.NAME -> compareBy<MediaPreviewRow> { it.displayName.lowercase() }
                    // 没有时间的排在最后：用 "~" 占位，比任何时间字符串都靠后。
                    MediaSort.ORIGINAL -> compareBy { it.currentTime?.toString() ?: "~" }
                    MediaSort.TARGET -> compareBy { it.targetTime?.toString() ?: "~" }
                }
            val sorted = rows.sortedWith(comparator)
            if (descending) sorted.reversed() else sorted
        }
    val processable = rows.count { it.included && it.targetTime != null }
    // "跳过"统一指"这一项没有可写入的时间"，两个页面对同一个意思只用这一个词。
    val skipped = rows.count { it.targetTime == null }

    Column(
        // 底部留出一份间距，动作条不要贴着底部导航栏。
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeadline(icon = headlineIcon, title = headlineTitle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                MediaCountSummary(
                    total = rows.size,
                    primaryCount = processable,
                    primaryLabel = primaryCountLabel,
                    secondaryCount = skipped,
                    secondaryLabel = "跳过",
                )
            }
            // 排序入口收成一个图标：点开是"排什么 + 正反序"两组选项，当前项打勾。
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "排序方式")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MediaSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                sort = option
                                menuOpen = false
                            },
                            trailingIcon = { MenuCheck(selected = sort == option) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    listOf(true, false).forEach { ascending ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (ascending) "升序" else "降序",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            onClick = {
                                descending = !ascending
                                menuOpen = false
                            },
                            trailingIcon = { MenuCheck(selected = descending != ascending) },
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(ordered, key = { it.id }) { row ->
                MediaTimeRowCard(
                    displayName = row.displayName,
                    reason = row.reason,
                    currentText = row.currentTime?.atZone(zoneId)?.format(MediaTimeFormatter),
                    targetText = row.targetTime?.atZone(zoneId)?.format(MediaTimeFormatter),
                    included = row.included,
                    thumbnailUri = row.thumbnailUri,
                    onToggle = { checked -> onToggleRow(row.id, checked) },
                    onEdit = { onEditRow(row.id) },
                    onPreview = { onPreviewRow(row.id) },
                )
            }
        }
        MediaActionBar(
            secondaryLabel = "上一步",
            onSecondary = onBack,
            primaryLabel = primaryLabel,
            onPrimary = onApply,
            primaryIcon = primaryIcon,
            primaryEnabled = primaryEnabled,
        )
    }
}

/** 排序菜单里表示"当前选中"的对勾；未选中时留白，保证文字左边缘对齐。 */
@Composable
private fun MenuCheck(selected: Boolean) {
    if (selected) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
