package dev.hk256.retimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoCameraBack
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.core.DateCandidate
import dev.hk256.retimer.core.FilenameDateParser
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.data.FilenameRuleState
import dev.hk256.retimer.data.UserPreferences
import dev.hk256.retimer.data.editZoneOf
import dev.hk256.retimer.media.MediaMetadataReader
import dev.hk256.retimer.media.MediaStoreRepository
import dev.hk256.retimer.media.MediaStoreUris
import dev.hk256.retimer.ui.components.ExpandingSectionCard
import dev.hk256.retimer.ui.components.ExpressiveListItem
import dev.hk256.retimer.ui.components.ExpressiveSwitchItem
import dev.hk256.retimer.ui.components.FilledInfoCard
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.SectionCardItemCorner
import dev.hk256.retimer.ui.theme.AppMotion
import dev.hk256.retimer.ui.theme.AppTypeScale
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 自动更正时间的数据来源，对应界面上的三个主标签页。 */
private enum class RepairSource(val label: String) {
    METADATA("元数据"),
    FILENAME("文件名"),
    MODIFIED("修改时间"),
}

/** 流程阶段；顺序即进入流程的深度，返回时按相反方向播放过渡动画。 */
private enum class RepairStage { CONFIG, RULES, PREVIEW, RESULT }

private data class RepairSourceInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private data class RepairRow(
    val item: MediaItem,
    val candidate: DateCandidate,
    /** 文件里读到的日期；媒体库没有记录时（SAF 选中的文件）用它显示「当前」。 */
    val fileDate: Instant? = null,
    val included: Boolean = false,
)

private fun RepairSource.info(): RepairSourceInfo =
    when (this) {
        RepairSource.METADATA ->
            RepairSourceInfo(
                icon = Icons.Rounded.PhotoCameraBack,
                title = "使用元数据更正时间",
                description = "启用「同步文件修改时间」后，可使用文件中的元数据来更正相册中图片和视频的顺序",
            )
        RepairSource.FILENAME ->
            RepairSourceInfo(
                icon = Icons.Rounded.TextFields,
                title = "使用文件名更正时间",
                description = "从文件名中按规则解析时间作为候选值",
            )
        RepairSource.MODIFIED ->
            RepairSourceInfo(
                icon = Icons.Rounded.Schedule,
                title = "使用文件修改时间更正时间",
                description = "使用文件系统记录的修改时间作为候选值",
            )
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RepairMediaPage(
    filenameRules: FilenameRuleState,
    onSelectionCountChange: (Int) -> Unit,
    onFlowActiveChange: (Boolean) -> Unit,
    /** 换阶段时回调：新阶段的滚动内容停在顶部，外壳据此复位顶栏的"已滚动"状态。 */
    onTopBarScrollReset: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { MediaStoreRepository(context.contentResolver) }
    val mediaStoreUris = remember { MediaStoreUris(context) }
    val preferences = remember { UserPreferences(context) }
    // 墙上时钟的解释时区：设置里选了固定偏移就用它，否则跟随设备。
    // 文件名里的时间、EXIF 里没写偏移的时间都按它解释。
    val zoneId = remember(preferences.editZoneOffsetSeconds) { editZoneOf(preferences.editZoneOffsetSeconds) }
    val metadataReader = remember(zoneId) { MediaMetadataReader(context.contentResolver, zoneId) }
    val parser = remember(zoneId) { FilenameDateParser(zoneId) }
    val writer = rememberMediaWriteController(repository, mediaStoreUris, zoneId, preferences)
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(RepairSource.METADATA) }
    /** 「覆盖已存在的日期字段」记得用户上次的选择。 */
    var overwrite by remember { mutableStateOf(preferences.overwriteExistingDateFields) }
    var advanced by remember { mutableStateOf(true) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var rows by remember { mutableStateOf<List<RepairRow>>(emptyList()) }
    var stage by remember { mutableStateOf(RepairStage.CONFIG) }
    var sheetOpen by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    // 进度是 Float：用 mutableFloatStateOf 避免每次更新都装箱。
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var analysisLabel by remember { mutableStateOf("") }
    var confirmApply by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RepairRow?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    /** 这次打开选媒体弹窗是为了追加，而不是替换已选的媒体。 */
    var pendingAppend by remember { mutableStateOf(false) }
    /** 交给选媒体回调的追加标记，在真正发起选择时定下来。 */
    var appendSelection by remember { mutableStateOf(false) }

    // 上报给外壳：有已选媒体时切换页面要二次确认；处在流程里时隐藏底部导航、顶栏换成返回按钮。
    // 流程标记用 SideEffect 上报：本帧绘制前执行，外壳与阶段切换同帧生效，内容区不会中途变高。
    LaunchedEffect(media.size) { onSelectionCountChange(media.size) }
    SideEffect { onFlowActiveChange(stage != RepairStage.CONFIG) }
    LaunchedEffect(stage) { onTopBarScrollReset() }

    /** 按当前来源与设置生成候选并进入预览；调用方负责维护 [analyzing] 状态。 */
    suspend fun runAnalysis() {
        val currentMedia = media
        if (currentMedia.isEmpty()) return
        val newRows =
            withContext(Dispatchers.IO) {
                currentMedia.mapIndexed { index, item ->
                    analysisProgress = (index + 1).toFloat() / currentMedia.size
                    analysisLabel = item.displayName
                    val metadata = metadataReader.readImageTime(repository.uriFor(item))
                    val candidate =
                        when (source) {
                            RepairSource.METADATA ->
                                DateCandidate(
                                    value = metadata,
                                    reason = if (metadata != null) "元数据中的拍摄日期" else "元数据中没有日期",
                                )
                            RepairSource.FILENAME ->
                                parser
                                    .findBestMatch(item.displayName, filenameRules.settings.enabledRules)
                                    ?.let { match ->
                                        DateCandidate(
                                            value = match.value,
                                            reason = "文件名规则：${match.rule.pattern}",
                                        )
                                    } ?: DateCandidate(null, "文件名未匹配")
                            RepairSource.MODIFIED ->
                                DateCandidate(
                                    value = item.fileModifiedTime,
                                    reason = "文件修改时间",
                                )
                        }
                    // 文件里已经有日期字段的项目默认不勾选：它可能是有意保留的日期，
                    // 只有用户显式开启"覆盖已存在的日期字段"时才一并勾选。
                    val eligible = candidate.value != null && (overwrite || metadata == null)
                    RepairRow(item, candidate, fileDate = metadata, included = eligible)
                }
            }
        rows = newRows
        stage = RepairStage.PREVIEW
    }

    /** 手动（重新）分析：返回配置页后调整来源或选项，用 FAB 再跑一次。 */
    fun analyze() {
        if (media.isEmpty() || analyzing) return
        scope.launch {
            analyzing = true
            runAnalysis()
            analyzing = false
        }
    }

    val picker =
        rememberMediaPicker(repository, mediaStoreUris) { loaded ->
            if (appendSelection) {
                appendSelection = false
                // 追加：把新选的接在已选媒体后面（同一项只留一份），候选需要重新生成。
                media = (media + loaded).distinctBy { it.id }
                rows = emptyList()
                stage = RepairStage.CONFIG
            } else {
                media = loaded
                rows = emptyList()
                stage = RepairStage.CONFIG
                // 选择媒体后立即分析，不需要再点一次按钮。
                analyzing = true
                runAnalysis()
                analyzing = false
            }
        }

    fun openPicker(append: Boolean) {
        pendingAppend = append
        sheetOpen = true
    }

    fun clearSelection() {
        media = emptyList()
        rows = emptyList()
        stage = RepairStage.CONFIG
    }

    fun applyRepairs() {
        val selected = rows.filter { it.included && it.candidate.value != null }
        if (selected.isEmpty()) {
            resultMessage = "没有可修正的项目"
            stage = RepairStage.RESULT
            return
        }
        // 反查一次并记下地址：申请写入授权和后面真正写入用的是同一批地址。
        val targets =
            selected.map { row ->
                MediaWriteTarget(row.item, row.candidate.value!!, writer.mediaStoreUri(row.item))
            }
        writer.write(targets) { message ->
            resultMessage = message
            stage = RepairStage.RESULT
        }
    }

    fun leaveStage() {
        // 流程结束就清空选择：回到主页时是干净状态，下一轮从"选择媒体"重新开始。
        stage = RepairStage.CONFIG
        media = emptyList()
        rows = emptyList()
    }

    // 返回手势/返回键与“返回”按钮都反向播放进入时的过渡动画。
    BackHandler(enabled = stage != RepairStage.CONFIG) {
        if (stage == RepairStage.RESULT) leaveStage() else stage = RepairStage.CONFIG
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { mediaStageTransition(targetState.ordinal > initialState.ordinal) },
            label = "repairStage",
        ) { current ->
            when (current) {
                RepairStage.CONFIG ->
                    RepairConfigStage(
                        source = source,
                        onSourceChange = { source = it },
                        advanced = advanced,
                        onAdvancedToggle = { advanced = !advanced },
                        overwrite = overwrite,
                        onOverwriteChange = {
                            overwrite = it
                            preferences.overwriteExistingDateFields = it
                        },
                        syncModified = writer.syncModified,
                        onSyncModifiedChange = writer::changeSyncModified,
                        enabledRuleCount = filenameRules.settings.enabledCount,
                        totalRuleCount = filenameRules.settings.rules.size,
                        onOpenRules = { stage = RepairStage.RULES },
                        mediaCount = media.size,
                        hasAnalysis = rows.isNotEmpty(),
                        analyzing = analyzing,
                        onOpenPicker = { openPicker(append = false) },
                        onAddMore = { openPicker(append = true) },
                        onClearSelection = ::clearSelection,
                        onAnalyze = ::analyze,
                    )
                RepairStage.RULES -> FilenameRuleStage(state = filenameRules)
                RepairStage.PREVIEW ->
                    {
                        val previewRows =
                            remember(rows, media) {
                                // 每个媒体可读的 content:// 地址，供缩略图加载。
                                val thumbnails =
                                    media.associate { item ->
                                        item.id to runCatching { writer.writableUri(item) }.getOrNull()
                                    }
                                rows.map { row ->
                                    MediaPreviewRow(
                                        id = row.item.id,
                                        displayName = row.item.displayName,
                                        reason = row.candidate.reason ?: "无法生成候选",
                                        // 图库里没有记录时（例如 SAF 选中的文件）用文件里读到的日期显示「当前」。
                                        currentTime = row.item.metadataTime ?: row.fileDate,
                                        targetTime = row.candidate.value,
                                        included = row.included,
                                        thumbnailUri = thumbnails[row.item.id],
                                    )
                                }
                            }
                        MediaPreviewStage(
                            headlineIcon = Icons.Rounded.AutoFixHigh,
                            headlineTitle = "修正预览",
                            primaryCountLabel = "将修正",
                            primaryLabel =
                                "应用修正 ${previewRows.count { it.included && it.targetTime != null }} 项",
                            primaryIcon = Icons.Rounded.AutoFixHigh,
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
                            onBack = { stage = RepairStage.CONFIG },
                            onApply = { confirmApply = true },
                        )
                    }
                RepairStage.RESULT ->
                    MediaResultStage(
                        icon = Icons.Rounded.AutoFixHigh,
                        headline = "处理结果",
                        cardTitle = "本次修正已结束",
                        message = resultMessage ?: "处理完成",
                        onDone = ::leaveStage,
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

    if (analyzing || picker.loading) {
        MediaProgressDialog(
            // 读取阶段还没有进度可报，按 0 显示，与进入分析后的进度条保持同一个组件。
            progress = if (analyzing) analysisProgress else 0f,
            label = if (analyzing) analysisLabel else picker.loadingLabel,
        )
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
            title = { Text("应用 ${rows.count { it.included }} 项修正？", style = AppTypeScale.dialogTitle) },
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
                        applyRepairs()
                    },
                ) {
                    Text("确认", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) { Text("取消", style = MaterialTheme.typography.labelLarge) }
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
            currentTime = row.item.metadataTime,
            target = row.candidate.value,
            zoneId = zoneId,
            onDismiss = { editing = null },
            onSave = { value ->
                rows =
                    rows.map {
                        if (it.item.id == row.item.id) {
                            it.copy(
                                candidate = it.candidate.copy(value = value),
                                included = true,
                            )
                        } else {
                            it
                        }
                    }
                editing = null
            },
        )
    }
}

/** 首屏：标签页选择日期来源，卡片说明来源，分组列表提供选项。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RepairConfigStage(
    source: RepairSource,
    onSourceChange: (RepairSource) -> Unit,
    advanced: Boolean,
    onAdvancedToggle: () -> Unit,
    overwrite: Boolean,
    onOverwriteChange: (Boolean) -> Unit,
    syncModified: Boolean,
    onSyncModifiedChange: (Boolean) -> Unit,
    enabledRuleCount: Int,
    totalRuleCount: Int,
    onOpenRules: () -> Unit,
    mediaCount: Int,
    hasAnalysis: Boolean,
    analyzing: Boolean,
    onOpenPicker: () -> Unit,
    onAddMore: () -> Unit,
    onClearSelection: () -> Unit,
    onAnalyze: () -> Unit,
) {
    val showFormatItem = source == RepairSource.FILENAME
    val advancedItemCount = 2

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp),
        ) {
            // 标题与标签页同属页头，两者间距比区块间距更近
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScreenHeadline(icon = Icons.Rounded.AutoFixHigh, title = "自动更正时间")
                PrimaryTabRow(
                    selectedTabIndex = source.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                ) {
                    RepairSource.entries.forEach { option ->
                        Tab(
                            selected = source == option,
                            onClick = { onSourceChange(option) },
                            text = {
                                Text(
                                    text = option.label,
                                    style = AppTypeScale.tabLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = source,
                transitionSpec = {
                    // 库内内容替换的做法：透明度进入 default、退出 fast；
                    // 尺寸变化交给 SizeTransform，用库内同一处（DatePicker）使用的 defaultSpatial。
                    (
                        fadeIn(AppMotion.defaultEffects<Float>()) togetherWith fadeOut(AppMotion.fastEffects<Float>())
                    ).using(
                        SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ -> AppMotion.defaultSpatial() },
                        ),
                    )
                },
                label = "repairSource",
            ) { current ->
                val info = current.info()
                FilledInfoCard(icon = info.icon, title = info.title, description = info.description)
            }
            // 已选媒体排在介绍卡片下面（从上到下仅次于介绍卡片）；
            // 与"文件名解析规则"同一套进出动画：选好媒体后从上方卡片底下滑入。
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
            // 文件名解析规则与"选项"同级，切换到文件名标签时才从说明卡片下方滑出。
            // 间距写在动画内容内部：隐藏时整块高度为 0，外层用显式 Spacer 保证始终只有一份间距，
            // 避免 Arrangement.spacedBy 给 0 高度子项上下各补一次导致的"双倍间距 + 瞬移"。
            AnimatedVisibility(
                visible = showFormatItem,
                // 滑入时顶部边缘被裁掉，看起来是从上方卡片底下滑入
                modifier = Modifier.clipToBounds(),
                enter = expandingCardEnter(),
                exit = expandingCardExit(),
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    ExpressiveListItem(
                        index = 0,
                        count = 1,
                        icon = Icons.Rounded.TextFields,
                        headline = "文件名解析规则",
                        supporting = "已启用 $enabledRuleCount / $totalRuleCount 种规则",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        headlineStyle = MaterialTheme.typography.titleMedium,
                        headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContainerColor = Color.Transparent,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconOffset = 4.dp,
                        onClick = onOpenRules,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        },
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
            onClick = if (mediaCount == 0) onOpenPicker else onAnalyze,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = {
                Icon(
                    imageVector = if (mediaCount == 0) Icons.Rounded.AddPhotoAlternate else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
            },
            text = {
                Text(
                    text =
                        when {
                            mediaCount == 0 -> "选择媒体"
                            analyzing -> "正在分析"
                            hasAnalysis -> "重新分析"
                            else -> "开始分析"
                        },
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
    }
}
