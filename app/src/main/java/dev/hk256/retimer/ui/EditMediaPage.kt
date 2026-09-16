package dev.hk256.retimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.core.TimeTransform
import dev.hk256.retimer.data.UserPreferences
import dev.hk256.retimer.media.MediaMetadataReader
import dev.hk256.retimer.media.MediaStoreRepository
import dev.hk256.retimer.media.MediaStoreUris
import dev.hk256.retimer.ui.components.ExpandingSectionCard
import dev.hk256.retimer.ui.components.ExpressiveSwitchItem
import dev.hk256.retimer.ui.components.FilledInfoCard
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.SectionCardItemCorner
import dev.hk256.retimer.ui.theme.AppTypeScale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 目标时间的设置方式。
 *
 * 默认不选中任何一项；选中后按 [needsDate] / [needsTime] 决定下面出现哪些填写组件
 *
 * [description] 支持 HTML 标签（例如 `<b>日期</b>`），渲染时会按标签加样式
 *
 * 文案里如果出现字面量的 < 或 &，要按 HTML 规则写成 &lt; / &amp;
 */
private enum class TargetTimeMode(
    val label: String,
    val description: String,
    val needsDate: Boolean,
    val needsTime: Boolean,
) {
    PER_FILE(
        label = "每个文件单独设置时间",
        description = "进入预览后逐个文件编辑日期和时间",
        needsDate = false,
        needsTime = false,
    ),
    DATE_AND_TIME(
        label = "统一设置日期和时间",
        description = "所有文件都改成<b>同一个日期和时间</b>",
        needsDate = true,
        needsTime = true,
    ),
    DATE_ONLY(
        label = "统一设置日期",
        description = "所有文件都改成同一个<b>日期</b>，各自保留原来的<b>时间</b>",
        needsDate = true,
        needsTime = false,
    ),
    TIME_ONLY(
        label = "统一设置时间",
        description = "所有文件都改成同一个<b>时间</b>，各自保留原来的<b>日期</b>",
        needsDate = false,
        needsTime = true,
    ),
    SHIFT(
        label = "按原始时间间隔平移",
        description = "把最早的文件改到指定时间，其余文件按各自与它的间隔一起平移",
        needsDate = true,
        needsTime = true,
    ),
}

private enum class EditMediaStage { CONFIG, TARGET_TIME, PREVIEW, RESULT }

/** 目标时间页里的六个数值格；回车按年 → 月 → 日 → 时 → 分 → 秒往前跳。 */
private enum class NumberFieldSlot { YEAR, MONTH, DAY, HOUR, MINUTE, SECOND }

/**
 * 回车跳格时的一次性标记。
 *
 * 只有"从上一个数值格按回车跳过来"才需要把光标放到数值末尾；用户自己点进某一格时，
 * 光标应该落在他点到的位置，所以这里只在跳格时置位、目标格拿到焦点后立刻消费掉。
 */
private class NumberFieldJump {
    private var caretToEnd = false

    fun requestCaretToEnd() {
        caretToEnd = true
    }

    fun consumeCaretToEnd(): Boolean {
        val pending = caretToEnd
        caretToEnd = false
        return pending
    }
}

/** 目标时间页里填写的年月日与时分秒，全部按字符串保存，校验统一在解析时做。 */
private data class DateTimeFields(
    val year: String = "",
    val month: String = "",
    val day: String = "",
    val hour: String = "",
    val minute: String = "",
    val second: String = "",
)

private data class EditRow(
    val item: MediaItem,
    val target: Instant?,
    /** 目标时间的来源说明，显示在文件名下方。 */
    val reason: String?,
    /** 文件里读到的日期；媒体库没有记录时用它显示「当前」。 */
    val fileDate: Instant? = null,
    val included: Boolean = false,
)

private fun parseDate(fields: DateTimeFields): LocalDate? {
    if (fields.year.length != 4 || fields.month.isEmpty() || fields.day.isEmpty()) return null
    val year = fields.year.toIntOrNull() ?: return null
    val month = fields.month.toIntOrNull() ?: return null
    val day = fields.day.toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private fun parseTime(fields: DateTimeFields): LocalTime? {
    if (fields.hour.length != 2 || fields.minute.length != 2 || fields.second.length != 2) return null
    val hour = fields.hour.toIntOrNull() ?: return null
    val minute = fields.minute.toIntOrNull() ?: return null
    val second = fields.second.toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
        LocalTime.of(hour, minute, second)
    } else {
        null
    }
}

private fun Instant.toFields(zoneId: ZoneId): DateTimeFields {
    val local = atZone(zoneId)
    return DateTimeFields(
        year = local.year.toString(),
        month = local.monthValue.toString().padStart(2, '0'),
        day = local.dayOfMonth.toString().padStart(2, '0'),
        hour = local.hour.toString().padStart(2, '0'),
        minute = local.minute.toString().padStart(2, '0'),
        second = local.second.toString().padStart(2, '0'),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditMediaPage(
    onSelectionCountChange: (Int) -> Unit,
    onFlowActiveChange: (Boolean) -> Unit,
    /** 换阶段时回调：新阶段的滚动内容停在顶部，外壳据此复位顶栏的"已滚动"状态。 */
    onTopBarScrollReset: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { MediaStoreRepository(context.contentResolver) }
    val mediaStoreUris = remember { MediaStoreUris(context) }
    val metadataReader = remember { MediaMetadataReader(context.contentResolver) }
    val zoneId = remember { ZoneId.systemDefault() }
    val preferences = remember { UserPreferences(context) }
    val writer = rememberMediaWriteController(repository, mediaStoreUris, zoneId, preferences)
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    /** 文件里读到的日期：用来判断"文件里已存在的日期字段"和显示「当前」。 */
    var fileDates by remember { mutableStateOf<Map<String, Instant?>>(emptyMap()) }
    var rows by remember { mutableStateOf<List<EditRow>>(emptyList()) }
    var stage by remember { mutableStateOf(EditMediaStage.CONFIG) }
    var mode by remember { mutableStateOf<TargetTimeMode?>(null) }
    var advanced by remember { mutableStateOf(true) }
    /** 「覆盖已存在的日期字段」记得用户上次的选择。 */
    var overwrite by remember { mutableStateOf(preferences.overwriteExistingDateFields) }
    var fields by remember { mutableStateOf(DateTimeFields()) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditRow?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    /** 这次打开选媒体弹窗是为了追加，而不是替换已选的媒体。 */
    var pendingAppend by remember { mutableStateOf(false) }
    /** 交给选媒体回调的追加标记，在真正发起选择时定下来。 */
    var appendSelection by remember { mutableStateOf(false) }

    // 上报给外壳：有已选媒体时切换页面要二次确认；处在流程里时隐藏底部导航、顶栏换成返回按钮。
    //
    // 流程标记用 SideEffect 上报：它在本帧绘制前执行，外壳（隐藏底部导航 = 改变内容区高度）
    // 会和阶段切换在同一帧生效；如果用 LaunchedEffect，外壳会晚一帧才变，内容区在过渡中途
    // 突然变高，贴底的东西会跳一下。
    LaunchedEffect(media.size) { onSelectionCountChange(media.size) }
    SideEffect { onFlowActiveChange(stage != EditMediaStage.CONFIG) }
    LaunchedEffect(stage) { onTopBarScrollReset() }

    /** 目标时间输入框的初值：取所选媒体里最早的时间，取不到就用此刻。 */
    fun seedFields(items: List<MediaItem>) {
        val seed = items.mapNotNull { it.metadataTime }.minOrNull() ?: Instant.now()
        fields = seed.toFields(zoneId)
    }

    val picker =
        rememberMediaPicker(repository, mediaStoreUris) { loaded ->
            val append = appendSelection
            appendSelection = false
            // 读一遍文件里的日期：既作为「当前」显示，也用来判断文件里是否已经写了日期。
            val dates =
                withContext(Dispatchers.IO) {
                    loaded.associate { item -> item.id to metadataReader.readImageTime(repository.uriFor(item)) }
                }
            fileDates = if (append) fileDates + dates else dates
            // 追加时保留已选媒体（同一项只留一份），已填好的目标时间也不动。
            media = if (append) (media + loaded).distinctBy { it.id } else loaded
            rows = emptyList()
            if (!append) {
                seedFields(loaded)
                stage = EditMediaStage.TARGET_TIME
            }
        }

    fun openPicker(append: Boolean) {
        pendingAppend = append
        sheetOpen = true
    }

    fun clearSelection() {
        media = emptyList()
        fileDates = emptyMap()
        rows = emptyList()
        stage = EditMediaStage.CONFIG
    }

    /**
     * 「覆盖已存在的日期字段」关闭时，文件里已经有日期的项目默认不勾选；
     * 手动编辑过的项目始终勾选，因为那是用户明确指定的。
     */
    fun defaultIncluded(item: MediaItem, target: Instant?): Boolean =
        target != null && (overwrite || fileDates[item.id] == null)

    /** 按当前模式与填写内容生成预览行。 */
    fun buildRows(): List<EditRow> {
        val current = media
        val chosen = mode ?: return emptyList()
        val date = parseDate(fields)
        val time = parseTime(fields)
        val target = if (date != null && time != null) date.atTime(time).atZone(zoneId).toInstant() else null
        fun row(item: MediaItem, itemTarget: Instant?, reason: String?) =
            EditRow(
                item = item,
                target = itemTarget,
                reason = reason,
                fileDate = fileDates[item.id],
                included = defaultIncluded(item, itemTarget),
            )
        return when (chosen) {
            // 逐个文件设置时没有统一的目标时间，保留上一轮在预览里逐项填好的值，
            // 避免"上一步看一眼再下一步"就把手工填的时间清掉。
            TargetTimeMode.PER_FILE ->
                current.map { item ->
                    val existing = rows.firstOrNull { it.item.id == item.id }
                    EditRow(
                        item = item,
                        target = existing?.target,
                        reason = existing?.reason ?: "进入预览后逐项编辑",
                        fileDate = fileDates[item.id],
                        included = existing?.included ?: false,
                    )
                }
            TargetTimeMode.DATE_AND_TIME -> {
                val targets = TimeTransform.uniform(current, target ?: return emptyList())
                current.map { row(it, targets[it.id], "统一设置日期和时间") }
            }
            TargetTimeMode.DATE_ONLY -> {
                val targets = TimeTransform.uniformDate(current, date ?: return emptyList(), zoneId)
                current.map { row(it, targets[it.id], "统一设置日期，保留原时间") }
            }
            TargetTimeMode.TIME_ONLY -> {
                val targets = TimeTransform.uniformTime(current, time ?: return emptyList(), zoneId)
                current.map { item ->
                    row(
                        item,
                        targets[item.id],
                        if (targets[item.id] != null) "统一设置时间，保留原日期" else "原有日期未知，请逐项设置",
                    )
                }
            }
            TargetTimeMode.SHIFT -> {
                val targets = TimeTransform.shiftFromEarliest(current, target ?: return emptyList())
                current.map { item ->
                    row(
                        item,
                        targets[item.id],
                        if (targets[item.id] != null) "按原始间隔平移" else "原有时间未知，请逐项设置",
                    )
                }
            }
        }
    }

    fun leaveFlow() {
        // 流程结束就清空选择：回到主页时是干净状态，下一轮从"选择媒体"重新开始。
        stage = EditMediaStage.CONFIG
        media = emptyList()
        fileDates = emptyMap()
        rows = emptyList()
        fields = DateTimeFields()
    }

    fun applyEdits() {
        val selected = rows.filter { it.included && it.target != null }
        if (selected.isEmpty()) {
            resultMessage = "没有可修改的项目"
            stage = EditMediaStage.RESULT
            return
        }
        val targets =
            selected.map { row -> MediaWriteTarget(row.item, row.target!!, writer.mediaStoreUri(row.item)) }
        writer.write(targets) { message ->
            resultMessage = message
            stage = EditMediaStage.RESULT
        }
    }

    // 返回手势/返回键与页面里的"上一步"一致，反向播放进入时的过渡动画。
    BackHandler(enabled = stage != EditMediaStage.CONFIG) {
        stage =
            when (stage) {
                EditMediaStage.RESULT -> {
                    // 与结果页的"完成"一致：流程结束就清空选择。
                    leaveFlow()
                    EditMediaStage.CONFIG
                }
                EditMediaStage.PREVIEW -> EditMediaStage.TARGET_TIME
                EditMediaStage.TARGET_TIME -> EditMediaStage.CONFIG
                EditMediaStage.CONFIG -> EditMediaStage.CONFIG
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { mediaStageTransition(targetState.ordinal > initialState.ordinal) },
            label = "editMediaStage",
        ) { current ->
            when (current) {
                EditMediaStage.CONFIG ->
                    EditConfigStage(
                        advanced = advanced,
                        onAdvancedToggle = { advanced = !advanced },
                        overwrite = overwrite,
                        onOverwriteChange = {
                            overwrite = it
                            preferences.overwriteExistingDateFields = it
                        },
                        syncModified = writer.syncModified,
                        onSyncModifiedChange = writer::changeSyncModified,
                        mediaCount = media.size,
                        onOpenPicker = { openPicker(append = false) },
                        onAddMore = { openPicker(append = true) },
                        onClearSelection = ::clearSelection,
                        onContinue = { stage = EditMediaStage.TARGET_TIME },
                    )
                EditMediaStage.TARGET_TIME ->
                    TargetTimeStage(
                        mode = mode,
                        onModeChange = { mode = it },
                        fields = fields,
                        onFieldsChange = { fields = it },
                        onBack = { stage = EditMediaStage.CONFIG },
                        onNext = {
                            val built = buildRows()
                            if (built.isNotEmpty()) {
                                rows = built
                                stage = EditMediaStage.PREVIEW
                            }
                        },
                    )
                EditMediaStage.PREVIEW ->
                    {
                        val previewRows =
                            remember(rows, media) {
                                val thumbnails =
                                    media.associate { item ->
                                        item.id to runCatching { writer.writableUri(item) }.getOrNull()
                                    }
                                rows.map { row ->
                                    MediaPreviewRow(
                                        id = row.item.id,
                                        displayName = row.item.displayName,
                                        reason = row.reason,
                                        // 图库里没有记录时用文件里读到的日期显示「当前」。
                                        currentTime = row.item.metadataTime ?: row.fileDate,
                                        targetTime = row.target,
                                        included = row.included,
                                        thumbnailUri = thumbnails[row.item.id],
                                    )
                                }
                            }
                        MediaPreviewStage(
                            headlineIcon = Icons.Rounded.EditCalendar,
                            headlineTitle = "编辑预览",
                            primaryCountLabel = "将修改",
                            primaryLabel =
                                "应用修改 ${previewRows.count { it.included && it.targetTime != null }} 项",
                            primaryIcon = Icons.Rounded.Edit,
                            zoneId = zoneId,
                            rows = previewRows,
                            onToggleRow = { id, checked ->
                                rows = rows.map { if (it.item.id == id) it.copy(included = checked) else it }
                            },
                            onEditRow = { id -> editing = rows.firstOrNull { it.item.id == id } },
                            onPreviewRow = { id ->
                                val item = media.firstOrNull { it.id == id }
                                if (item != null && !context.openMediaPreview(item, writer.writableUri(item))) {
                                    previewMessage = "没有可以打开「${item.displayName}」的应用"
                                }
                            },
                            onBack = { stage = EditMediaStage.TARGET_TIME },
                            onApply = { confirmApply = true },
                            // 一个可写的目标时间都没有（例如刚进入"逐个文件设置"）时先不能应用。
                            primaryEnabled = previewRows.any { it.included && it.targetTime != null },
                        )
                    }
                EditMediaStage.RESULT ->
                    MediaResultStage(
                        icon = Icons.Rounded.EditCalendar,
                        headline = "处理结果",
                        cardTitle = "本次修改已结束",
                        message = resultMessage ?: "处理完成",
                        onDone = ::leaveFlow,
                    )
            }
        }
    }

    if (sheetOpen) {
        MediaSourceSheet(
            onDismiss = { sheetOpen = false },
            onPickVisual = {
                sheetOpen = false
                appendSelection = pendingAppend
                picker.pickVisual()
            },
            onPickDocument = {
                sheetOpen = false
                appendSelection = pendingAppend
                picker.pickDocument()
            },
            onPickTree = {
                sheetOpen = false
                appendSelection = pendingAppend
                picker.pickTree()
            },
        )
    }

    if (picker.loading) {
        MediaProgressDialog(progress = null, label = picker.loadingLabel)
    }

    if (writer.allFilesDialogVisible) {
        MediaAllFilesAccessDialog(
            onDismiss = writer::dismissAllFilesDialog,
            onConfirm = writer::requestAllFilesAccess,
        )
    }

    if (confirmApply) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = {
                Text(
                    text = "应用 ${rows.count { it.included && it.target != null }} 项修改？",
                    style = AppTypeScale.dialogTitle,
                )
            },
            text = {
                Text(
                    if (writer.syncModified) {
                        "将写入媒体元数据，并同步文件修改时间。"
                    } else {
                        "将写入媒体元数据（不改文件修改时间）。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmApply = false
                        applyEdits()
                    },
                ) {
                    Text("确认", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }

    previewMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { previewMessage = null },
            title = { Text("无法预览", style = AppTypeScale.dialogTitle) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { previewMessage = null }) {
                    Text("好", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }

    editing?.let { row ->
        MediaTimeEditDialog(
            currentTime = row.item.metadataTime ?: row.fileDate,
            target = row.target,
            zoneId = zoneId,
            onDismiss = { editing = null },
            onSave = { value ->
                rows =
                    rows.map {
                        if (it.item.id == row.item.id) {
                            it.copy(target = value, reason = "手动编辑", included = true)
                        } else {
                            it
                        }
                    }
                editing = null
            },
        )
    }
}

/** 首屏：与修正页同一套排版，只是没有选择修正类型的标签页。 */
@Composable
private fun EditConfigStage(
    advanced: Boolean,
    onAdvancedToggle: () -> Unit,
    overwrite: Boolean,
    onOverwriteChange: (Boolean) -> Unit,
    syncModified: Boolean,
    onSyncModifiedChange: (Boolean) -> Unit,
    mediaCount: Int,
    onOpenPicker: () -> Unit,
    onAddMore: () -> Unit,
    onClearSelection: () -> Unit,
    onContinue: () -> Unit,
) {
    val advancedItemCount = 2

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
        ) {
            ScreenHeadline(icon = Icons.Rounded.EditCalendar, title = "编辑媒体时间")
            Spacer(Modifier.height(16.dp))
            FilledInfoCard(
                icon = Icons.Rounded.Edit,
                title = "手动编辑时间",
                description = "选择照片或视频后手动设定时间：可以统一设置日期和时间、仅更改日期或时间、按原有间隔平移等；也可在进入预览后逐个文件调整",
            )
            // 已选媒体排在介绍卡片下面（从上到下仅次于介绍卡片）；
            // 与"文件名解析规则"同一套进出动画
            AnimatedVisibility(
                visible = mediaCount > 0,
                modifier = Modifier.clipToBounds(),
                enter = expandingCardEnter(),
                exit = expandingCardExit(),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    SelectedMediaCard(
                        count = mediaCount,
                        onAddMore = onAddMore,
                        onClear = onClearSelection,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            ExpandingSectionCard(
                title = "选项",
                icon = Icons.Rounded.Tune,
                expanded = advanced,
                onToggle = onAdvancedToggle,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                // index 必须按渲染顺序给：最下面一项的下边缘贴着卡片底板，
                // 它的外圆角要用 SectionCardItemCorner（底板圆角减内边距）才能和底板同心。
                ExpressiveSwitchItem(
                    index = 0,
                    count = advancedItemCount,
                    icon = Icons.Rounded.Update,
                    headline = "同步文件修改时间",
                    supporting = "同时更改文件系统中的时间戳，需要「所有文件访问权限」",
                    checked = syncModified,
                    onCheckedChange = onSyncModifiedChange,
                    cornerRadius = 8.dp,
                    leadingIconOffset = 1.dp,
                    contentOffset = (-3).dp,
                )
                ExpressiveSwitchItem(
                    index = advancedItemCount - 1,
                    count = advancedItemCount,
                    icon = Icons.Rounded.Edit,
                    headline = "覆盖已存在的时间字段",
                    supporting = "开启后一并勾选文件里已有时间信息的项目",
                    checked = overwrite,
                    onCheckedChange = onOverwriteChange,
                    cornerRadius = SectionCardItemCorner,
                    leadingIconOffset = 1.dp,
                    contentOffset = (-3).dp,
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = if (mediaCount == 0) onOpenPicker else onContinue,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = {
                Icon(
                    imageVector = if (mediaCount == 0) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
            },
            text = {
                Text(
                    text = if (mediaCount == 0) "选择媒体" else "设置目标时间",
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
    }
}

/** 目标时间页：先选设置方式，再按需要填写日期/时间。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetTimeStage(
    mode: TargetTimeMode?,
    onModeChange: (TargetTimeMode) -> Unit,
    fields: DateTimeFields,
    onFieldsChange: (DateTimeFields) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val date = parseDate(fields)
    val dateInvalid = mode?.needsDate == true && date == null
    val timeInvalid = mode?.needsTime == true && parseTime(fields) == null
    val nextEnabled = mode != null && !dateInvalid && !timeInvalid

    // 回车跳格的顺序只包含当前显示的数值格：日历按钮不参与跳转。
    val visibleSlots =
        buildList {
            if (mode?.needsDate == true) {
                addAll(listOf(NumberFieldSlot.YEAR, NumberFieldSlot.MONTH, NumberFieldSlot.DAY))
            }
            if (mode?.needsTime == true) {
                addAll(listOf(NumberFieldSlot.HOUR, NumberFieldSlot.MINUTE, NumberFieldSlot.SECOND))
            }
        }
    val focusRequesters = remember { NumberFieldSlot.entries.associateWith { FocusRequester() } }
    val jump = remember { NumberFieldJump() }
    val focusManager = LocalFocusManager.current

    fun imeActionFor(slot: NumberFieldSlot): ImeAction =
        if (visibleSlots.lastOrNull() == slot) ImeAction.Done else ImeAction.Next

    /** 回车：跳到下一个数值格，并让它拿到焦点后把光标放到数值末尾。 */
    fun jumpToNext(from: NumberFieldSlot) {
        val next = visibleSlots.getOrNull(visibleSlots.indexOf(from) + 1)
        if (next == null) {
            // 已经是最后一格：没有下一格可跳，收起键盘。
            focusManager.clearFocus()
            return
        }
        jump.requestCaretToEnd()
        focusRequesters.getValue(next).requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeadline(icon = Icons.Rounded.EditCalendar, title = "设置目标时间")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = mode?.label ?: "选择设置模式",
                            // 菜单文字用常规字重，和加粗的按钮/标签区分开。
                            style = AppTypeScale.tabLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        TargetTimeMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    // 菜单项也用常规字重，和按钮、标签的加粗区分开。
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                },
                                onClick = {
                                    onModeChange(option)
                                    menuExpanded = false
                                },
                                trailingIcon = {
                                    if (mode == option) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
                // 菜单下方用小字说明当前这种方式会怎么改
                Text(
                    // Android 端 API：AnnotatedString.fromHtml 只在 ui-text 的 androidMain 里，
                    // 用来把说明文案里的 <b>…</b> 之类的标签转成样式（换平台要换实现）。
                    text =
                        AnnotatedString.fromHtml(
                            mode?.description ?: "选择一种模式后，下面会出现需要填写的<b>时间</b>。",
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mode?.needsDate == true) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberField(
                            label = "年",
                            value = fields.year,
                            maxLength = 4,
                            onValueChange = { onFieldsChange(fields.copy(year = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.YEAR),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.YEAR),
                            onImeNext = { jumpToNext(NumberFieldSlot.YEAR) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "月",
                            value = fields.month,
                            maxLength = 2,
                            onValueChange = { onFieldsChange(fields.copy(month = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.MONTH),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.MONTH),
                            onImeNext = { jumpToNext(NumberFieldSlot.MONTH) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "日",
                            value = fields.day,
                            maxLength = 2,
                            onValueChange = { onFieldsChange(fields.copy(day = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.DAY),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.DAY),
                            onImeNext = { jumpToNext(NumberFieldSlot.DAY) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { pickingDate = true }) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = "打开日历选择日期")
                        }
                    }
                    if (dateInvalid) {
                        FieldError("日期无效：年 4 位，月 1-12，日按当月天数")
                    }
                }
            }
            if (mode?.needsTime == true) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            label = "时",
                            value = fields.hour,
                            maxLength = 2,
                            onValueChange = { onFieldsChange(fields.copy(hour = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.HOUR),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.HOUR),
                            onImeNext = { jumpToNext(NumberFieldSlot.HOUR) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "分",
                            value = fields.minute,
                            maxLength = 2,
                            onValueChange = { onFieldsChange(fields.copy(minute = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.MINUTE),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.MINUTE),
                            onImeNext = { jumpToNext(NumberFieldSlot.MINUTE) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "秒",
                            value = fields.second,
                            maxLength = 2,
                            onValueChange = { onFieldsChange(fields.copy(second = it)) },
                            imeAction = imeActionFor(NumberFieldSlot.SECOND),
                            focusRequester = focusRequesters.getValue(NumberFieldSlot.SECOND),
                            onImeNext = { jumpToNext(NumberFieldSlot.SECOND) },
                            consumeCaretToEnd = jump::consumeCaretToEnd,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (timeInvalid) {
                        FieldError("时间无效：时 0-23，分 0-59，秒 0-59")
                    }
                }
            }
        }
        MediaActionBar(
            secondaryLabel = "上一步",
            onSecondary = onBack,
            primaryLabel = "下一步",
            onPrimary = onNext,
            primaryEnabled = nextEnabled,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    if (pickingDate) {
        // 日历内部用 UTC 零点表示"某一天"，所以这里统一按 UTC 换算。
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onFieldsChange(
                                fields.copy(
                                    year = picked.year.toString(),
                                    month = picked.monthValue.toString().padStart(2, '0'),
                                    day = picked.dayOfMonth.toString().padStart(2, '0'),
                                ),
                            )
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
 * 数值输入框：只收数字，回车（软键盘上的"下一个"）跳到下一个数值输入框。
 *
 * 光标只在"回车跳格"时被放到数值末尾（见 [consumeCaretToEnd]）：用户点进某一格时，
 * 光标仍然落在他点到的位置，可以正常插在数字中间修改。
 *
 * [imeAction] 给 [ImeAction.Done] 时收起键盘，不再往按钮上跳。
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    /** 回车跳格：跳到下一个数值格（不经过日历按钮之类的其他可聚焦控件）。 */
    onImeNext: () -> Unit,
    /** 这一格因为回车跳格而拿到焦点时返回 true，此时把光标放到数值末尾。 */
    consumeCaretToEnd: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // 初值把光标放在末尾；之后由输入本身决定，只有回车跳格才再次对齐到末尾。
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    // 外面改了值（预填、日历选中的日期）时同步文字，光标位置保持不变（越界时自动收到末尾）。
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val digits = newValue.text.filter(Char::isDigit).take(maxLength)
            // 保留输入光标的位置，只把非数字/超出的部分去掉。
            textFieldValue = newValue.copy(text = digits)
            onValueChange(digits)
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = dialogInputTextStyle(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions =
            KeyboardActions(
                onNext = { onImeNext() },
                onDone = { focusManager.clearFocus() },
            ),
        modifier =
            modifier
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused && consumeCaretToEnd()) {
                        textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
                    }
                },
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
