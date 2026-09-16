package dev.hk256.retimer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.ui.components.ExpressiveListItem
import dev.hk256.retimer.ui.components.FilledInfoCard
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.pressableSurface
import dev.hk256.retimer.ui.theme.AppTypeScale
import dev.hk256.retimer.ui.theme.AppMotion
import dev.hk256.retimer.ui.theme.warning
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 两个页面共用的时间显示格式。 */
internal val MediaTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")

/**
 * 卡片滑入的进场动画（文件名解析规则、已选择的媒体都用它）。
 *
 * 高度和位移必须用**同一条曲线**：高度按 p(t) 从 0 长到 h，内容同时按 -h·(1-p(t)) 上移，
 * 于是内容的下边缘任何时刻都贴着整块的下边缘（= 下方卡片的起点），下面的卡片会被"顶"着走。
 * 之前高度用 effects、位移用 spatial，两条弹簧刚度不同（1600 vs 700），进度不一致，
 * 块里的内容和下面的卡片就会各走各的，看起来"不同步"。
 * 尺寸与位置都属于空间变化，所以两者都用 spatial（进入 default、退出 fast）。
 */
internal fun expandingCardEnter(): EnterTransition =
    expandVertically(AppMotion.defaultSpatial(), expandFrom = Alignment.Top) +
        slideInVertically(AppMotion.defaultSpatial()) { height -> -height } +
        fadeIn(AppMotion.defaultEffects())

/** 与 [expandingCardEnter] 配对的退场动画，曲线同样一致（退出用 fast）。 */
internal fun expandingCardExit(): ExitTransition =
    shrinkVertically(AppMotion.fastSpatial(), shrinkTowards = Alignment.Top) +
        slideOutVertically(AppMotion.fastSpatial()) { height -> -height } +
        fadeOut(AppMotion.fastEffects())

/**
 * FAB 高度：与 M3 的 FAB 容器高度一致（FabPrimaryTokens.ContainerHeight = 56dp）。
 *
 * 显式写出来是为了让动作条里的两个 FAB 无论带不带图标都是同一个高度。
 */
private val MediaFabHeight = 56.dp

/**
 * 唤起系统预览查看一个媒体。
 *
 * 用 ACTION_VIEW 让系统自己挑能处理该 MIME 的应用（相册、播放器），
 * 这样不依赖任何具体应用的包名和界面。必须带 FLAG_GRANT_READ_URI_PERMISSION，
 * 否则接收方没有我们授予的读取权限，打开会是空白。
 *
 * 返回是否成功唤起；没有应用能处理时返回 false，由调用方给出提示。
 */
internal fun Context.openMediaPreview(item: MediaItem, uri: Uri): Boolean {
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return runCatching { startActivity(intent) }
        .onFailure { error ->
            // 没有匹配的 Activity 时系统抛 ActivityNotFoundException，这里不必区分具体原因。
            if (error !is ActivityNotFoundException) throw error
        }
        .isSuccess
}

/**
 * 编辑对话框里输入框的文字样式。
 *
 * M3 输入框默认用 bodyLarge（16sp），这里放大一号：框里就几个数字，
 * 大一点更好点、更好读。
 */
@Composable
internal fun dialogInputTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)

/**
 * 手动编辑一条媒体的时间。
 *
 * 一个对话框两行：日期行是一个只读字段，点它弹出 M3 原生的 [DatePickerDialog] 日历；
 * 时间行手工填时/分/秒——原生 TimePicker 只有分钟粒度，没有秒。
 * 保存时把两行合成一个瞬间值。
 *
 * 初值优先用已有的目标时间，其次是媒体当前时间，都没有才用此刻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaTimeEditDialog(
    currentTime: Instant?,
    target: Instant?,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onSave: (Instant) -> Unit,
) {
    val initial = remember(currentTime, target) {
        (target ?: currentTime ?: Instant.now()).atZone(zoneId)
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd") }
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var hourText by remember { mutableStateOf(initial.hour.toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    var secondText by remember { mutableStateOf(initial.second.toString().padStart(2, '0')) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑时间", style = AppTypeScale.dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRow(text = date.format(dateFormatter), onClick = { pickingDate = true })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2); error = null },
                        label = { Text("时") },
                        singleLine = true,
                        textStyle = dialogInputTextStyle(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2); error = null },
                        label = { Text("分") },
                        singleLine = true,
                        textStyle = dialogInputTextStyle(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = secondText,
                        onValueChange = { secondText = it.filter(Char::isDigit).take(2); error = null },
                        label = { Text("秒") },
                        singleLine = true,
                        textStyle = dialogInputTextStyle(),
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hour = hourText.toIntOrNull()
                    val minute = minuteText.toIntOrNull()
                    val second = secondText.toIntOrNull()
                    when {
                        hour == null || hour !in 0..23 -> error = "小时需在 0~23 之间"
                        minute == null || minute !in 0..59 -> error = "分钟需在 0~59 之间"
                        second == null || second !in 0..59 -> error = "秒需在 0~59 之间"
                        else ->
                            onSave(
                                date.atTime(LocalTime.of(hour, minute, second))
                                    .atZone(zoneId)
                                    .toInstant(),
                            )
                    }
                },
            ) {
                Text("保存", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelLarge) }
        },
    )

    if (pickingDate) {
        // 日历内部用 UTC 零点表示"某一天"，所以这里统一按 UTC 换算。
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        pickingDate = false
                    },
                ) {
                    Text("确定", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * 日期行：外观是同宽度的只读输入框，点击打开原生日历。
 *
 * 输入框设成 `enabled = false`（不是 readOnly：readOnly 仍会响应点击去放光标，
 * 覆盖层就抢不到事件了），再用覆盖层接管点击并显示涟漪，所以禁用态的颜色要手动
 * 映射回正常色，否则整行会变成灰掉的禁用样式。
 */
@Composable
internal fun DateRow(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            enabled = false,
            label = { Text("日期") },
            trailingIcon = {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            },
            textStyle = dialogInputTextStyle(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(onClick = onClick),
        )
    }
}

/**
 * 缩略图：加载真实图片，点击唤起系统预览。
 *
 * [uri] 为 null（拿不到可读地址）或加载失败时回退到占位图标。
 * 视频由 Application 里注册的 VideoFrameDecoder 取首帧，和图片共用同一条路径。
 */
@Composable
internal fun MediaThumbnail(
    uri: Uri?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val clickModifier =
        if (onClick != null) {
            Modifier.pressableSurface(shape = shape, containerColor = containerColor, onClick = onClick)
        } else {
            Modifier.clip(shape).background(containerColor)
        }
    Box(
        modifier = modifier.then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (uri == null) {
            // 拿不到可读地址：直接显示占位图标。
            ThumbnailPlaceholderIcon(clickable = onClick != null)
        } else {
            // 占位图标只在"图片真正画出来之前"顶班（含加载失败）：
            // 图片一旦就绪就把它撤掉，否则半透明图片会透出底下的图标。
            var loaded by remember(uri) { mutableStateOf(false) }
            AsyncImage(
                model = uri,
                contentDescription = if (onClick != null) "预览" else null,
                contentScale = ContentScale.Crop,
                onState = { state -> loaded = state is AsyncImagePainter.State.Success },
                modifier = Modifier.matchParentSize().clip(shape),
            )
            if (!loaded) {
                ThumbnailPlaceholderIcon(clickable = false)
            }
        }
    }
}

/** 缩略图占位图标；[clickable] 为 true 时由它承载无障碍描述。 */
@Composable
private fun ThumbnailPlaceholderIcon(clickable: Boolean) {
    Icon(
        imageVector = Icons.Rounded.Image,
        contentDescription = if (clickable) "预览" else null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 一条媒体的时间预览卡片：上半是文件名与说明，下半是"当前 → 更改"。
 *
 * 两个页面共用同一张卡片，只有文案由调用方给出。
 */
@Composable
internal fun MediaTimeRowCard(
    displayName: String,
    reason: String?,
    currentText: String?,
    targetText: String?,
    included: Boolean,
    thumbnailUri: Uri?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
) {
    // 只有确实存在一个与当前时间不同的目标时间才用强调色；
    // 比较格式化后的文本，避免两个时间只差不到一秒、看起来一样却变色。
    val targetEmphasized = targetText != null && targetText != currentText
    // 卡片不投影：它是列表里的一项，不是浮在内容上的容器。
    // 未勾选用中性容器角色（本身带一点绿），勾选后换成次级容器角色，绿色再深一档；
    // 标题颜色跟着容器角色走，符合 M3 的"容器 + 其上内容色"配对。
    val containerColor =
        if (included) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val titleColor =
        contentColorFor(containerColor).takeIf { it != Color.Unspecified }
            ?: MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 缩略图与右侧内容整体水平中线对齐。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 缩略图放在最左侧，作为每项内容的视觉锚点；点击唤起系统预览。
                MediaThumbnail(uri = thumbnailUri, onClick = onPreview, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                // "文件名 + 说明"是一个整体，复选框与这个整体的水平中线对齐。
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 文件名与说明共用一个左边缘，紧贴缩略图右侧。
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = reason ?: "无法生成候选",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(checked = included, onCheckedChange = onToggle)
                }
            }
            // 媒体信息与时间预览之间用分割线断开。
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            // 时间更改预览另起一行，左边缘与缩略图对齐。
            // "当前 + 更改"是一个整体，编辑按钮与这个整体的水平中线对齐。
            // 上下各 4dp：内容高度由 48dp 的编辑按钮决定，整段正好 56dp，与上方缩略图等高。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "当前：${currentText ?: "未设置"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "更改：${targetText ?: "无法生成"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (targetEmphasized) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "手动编辑时间")
                }
            }
        }
    }
}

/**
 * 页面底部的双按钮动作条。
 *
 * 两个都是 FAB：高度与首页的一致，宽度跟着文字长度走，分别贴住左右两边，中间留出空白。
 * 左侧「上一步」用次级容器色表示次级操作，右侧是主操作（可选图标，禁用时按 M3 的禁用配色）。
 * 这里的 FAB 不投影——它们是页面内的常驻控件，不是浮在内容上的按钮。
 */
@Composable
internal fun MediaActionBar(
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector? = null,
    primaryEnabled: Boolean = true,
) {
    val flatElevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    val disabledContainer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val primaryContainerColor =
        if (primaryEnabled) FloatingActionButtonDefaults.containerColor else disabledContainer
    val primaryContentColor =
        if (primaryEnabled) {
            contentColorFor(FloatingActionButtonDefaults.containerColor)
        } else {
            disabledContent
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtendedFloatingActionButton(
            onClick = onSecondary,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            elevation = flatElevation,
            modifier = Modifier.height(MediaFabHeight),
        ) {
            Text(text = secondaryLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        if (primaryIcon != null) {
            // 带图标时用 FAB 自带的"图标 + 文字"排版，和首页的 FAB 完全一致。
            ExtendedFloatingActionButton(
                text = {
                    Text(text = primaryLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                },
                icon = {
                    Icon(imageVector = primaryIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                },
                onClick = { if (primaryEnabled) onPrimary() },
                containerColor = primaryContainerColor,
                contentColor = primaryContentColor,
                elevation = flatElevation,
                modifier = Modifier.height(MediaFabHeight),
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = { if (primaryEnabled) onPrimary() },
                containerColor = primaryContainerColor,
                contentColor = primaryContentColor,
                elevation = flatElevation,
                modifier = Modifier.height(MediaFabHeight),
            ) {
                Text(text = primaryLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/**
 * 预览统计行：数字用 primary 加粗，其余文案保持 onSurfaceVariant，
 * 让"共几项 / 将处理几项 / 处理不了几项"三个数量能被一眼扫到。
 *
 * 第二个数量不为 0 时改用警告色：这一项代表有内容处理不了，需要区别于正常数量的强调色；
 * 为 0 时它不构成问题，保持 primary。error 留给真正的失败（写入失败等），这里只是"跳过"。
 */
@Composable
internal fun MediaCountSummary(
    total: Int,
    primaryCount: Int,
    primaryLabel: String,
    secondaryCount: Int,
    secondaryLabel: String,
) {
    val numberStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val warningStyle = SpanStyle(color = MaterialTheme.colorScheme.warning, fontWeight = FontWeight.Bold)
    val summary =
        buildAnnotatedString {
            append("共 ")
            withStyle(numberStyle) { append(total.toString()) }
            append(" 项 · $primaryLabel ")
            withStyle(numberStyle) { append(primaryCount.toString()) }
            append(" 项 · $secondaryLabel ")
            withStyle(if (secondaryCount != 0) warningStyle else numberStyle) {
                append(secondaryCount.toString())
            }
            append(" 项")
        }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 已选媒体数量的卡片：可以继续追加选择，也可以清空重来。
 *
 * 清空会连带丢掉已生成的时间设置，所以走一次二次确认。
 */
@Composable
internal fun SelectedMediaCard(count: Int, onAddMore: () -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "已选择的媒体",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$count 项",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onAddMore) {
                Text("选择更多", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { confirmClear = true }) {
                Text("清空选择", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空已选择的媒体？", style = AppTypeScale.dialogTitle) },
            text = { Text("将移除已选择的 $count 项媒体，已经生成的时间设置也会一起清空。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) {
                    Text("清空", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/**
 * 结果页：两个页面只有大标题的图标与文案不同。
 *
 * 卡片固定用"更新"图标：它表达的是"本次处理已经结束"，与具体页面无关。
 */
@Composable
internal fun MediaResultStage(
    icon: ImageVector,
    headline: String,
    cardTitle: String,
    message: String,
    onDone: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeadline(icon = icon, title = headline)
            FilledInfoCard(icon = Icons.Rounded.Update, title = cardTitle, description = message)
        }
        ExtendedFloatingActionButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(imageVector = Icons.Rounded.Check, contentDescription = null) },
            text = { Text("完成", style = MaterialTheme.typography.labelLarge) },
        )
    }
}

/** 选媒体的底部弹窗：相册选择器 / 文件选择器 / 文件夹选择器三个入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSourceSheet(
    onDismiss: () -> Unit,
    onPickVisual: () -> Unit,
    onPickDocument: () -> Unit,
    onPickTree: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                text = "选择媒体来源",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ExpressiveListItem(
                    index = 0,
                    count = 3,
                    icon = Icons.Rounded.PhotoLibrary,
                    headline = "选择照片和视频",
                    supporting = "仅授权所选媒体",
                    onClick = onPickVisual,
                )
                ExpressiveListItem(
                    index = 1,
                    count = 3,
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    headline = "选择文件",
                    supporting = "仅授权所选文件",
                    onClick = onPickDocument,
                )
                ExpressiveListItem(
                    index = 2,
                    count = 3,
                    icon = Icons.Rounded.FolderOpen,
                    headline = "选择文件夹",
                    supporting = "授权所选目录及其子目录",
                    onClick = onPickTree,
                )
            }
        }
    }
}

/**
 * 处理中的对话框：读取/分析/写入都复用同一种样式。
 *
 * [progress] 为 null 时显示不确定进度（拿不到总量时用）。
 */
@Composable
internal fun MediaProgressDialog(progress: Float?, label: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("正在分析", style = AppTypeScale.dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

/**
 * 「同步文件修改时间」需要「所有文件访问权限」，开启前先把原因和不开启的后果说清楚。
 */
@Composable
internal fun MediaAllFilesAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要「所有文件访问权限」", style = AppTypeScale.dialogTitle) },
        text = {
            Text(
                "「同步文件修改时间」将会更改文件在存储里的时间戳，" +
                    "应用需要「所有文件访问权限」才能写入这一数据。\n\n" +
                    "不开启则仍然会写入文件内的元数据和图库日期，但文件系统里的时间戳会更新为当前时间，" +
                    "这一选项会保持关闭。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("授予权限", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
