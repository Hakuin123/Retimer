package dev.hk256.retimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.core.FilenamePattern
import dev.hk256.retimer.core.FilenameRule
import dev.hk256.retimer.core.FilenameRuleSettings
import dev.hk256.retimer.data.FilenameRuleState
import dev.hk256.retimer.ui.components.FilledInfoCard
import dev.hk256.retimer.ui.components.LeadingIconCircle
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.SectionCardCorner
import dev.hk256.retimer.ui.components.listItemShape
import dev.hk256.retimer.ui.theme.AppTypeScale
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** 示例用的那一刻：固定不变，用户任何时候看到的示例都是同一个。 */
private val RuleSampleInstant: Instant = Instant.parse("2024-01-31T13:45:59Z")

/** 示例用的时区：固定 UTC，示例文字不随设备时区变。 */
private val RuleSampleZone: ZoneId = ZoneOffset.UTC

/**
 * 竖线的高度。
 *
 * 比原来左侧图标圆的直径（40dp）略高一点，上下都留出空档、不贴到行的边缘。
 * 用固定值而不是跟着行高走：行高会随规则换行而变，线跟着长会在长规则上显得很空。
 */
private val RuleDividerHeight = 48.dp

/** 竖线到开关、开关到右边缘之间留出的空隙。 */
private val RuleSwitchPadding = 16.dp

/** 编辑弹窗要编辑的对象：新建一条规则，或编辑列表里已有的那一条。 */
private sealed interface RuleEditorTarget {
    data object New : RuleEditorTarget

    data class Existing(val rule: FilenameRule) : RuleEditorTarget
}

/**
 * 文件名解析规则子页面。
 *
 * 列表里只有一种东西：初始规则和用户自己加的规则排在一起，一样能开关、编辑、删除。
 * 改动即时生效并落盘，所以页面没有"完成"按钮，返回就是结束。
 */
@Composable
internal fun FilenameRuleStage(state: FilenameRuleState) {
    val settings = state.settings
    var editorTarget by remember { mutableStateOf<RuleEditorTarget?>(null) }
    var deleting by remember { mutableStateOf<FilenameRule?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
        ) {
            ScreenHeadline(icon = Icons.Rounded.TextFields, title = "文件名解析规则")
            Spacer(Modifier.height(16.dp))
            FilledInfoCard(
                icon = Icons.Rounded.EditNote,
                title = "匹配格式说明",
                description = "点击规则以编辑匹配格式\n\n" +
                    "字段：yyyy 年、yy 两位年、MM 月、dd 日、HH 时、mm 分、ss 秒\n" +
                    "（月日时分秒也可以只写一个字母，匹配一位或两位数字）\n" +
                    "普通文本：写在单引号里，例如 'IMG'_yyyyMMdd\n" +
                    "时间戳：{unix} 秒、{unix_ms} 毫秒",
            )
            Spacer(Modifier.height(24.dp))
            RuleSectionTitle("规则")
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // 初始规则与用户自己加的规则在同一张表里：初始只是预先放在前面，别的没有区别。
                settings.rules.forEachIndexed { index, rule ->
                    FilenameRuleItem(
                        index = index,
                        count = settings.rules.size,
                        rule = rule,
                        enabled = settings.isEnabled(rule),
                        onEnabledChange = { state.setEnabled(rule, it) },
                        onEdit = { editorTarget = RuleEditorTarget.Existing(rule) },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editorTarget = RuleEditorTarget.New },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            text = { Text("添加规则", style = MaterialTheme.typography.labelLarge) },
        )
    }

    editorTarget?.let { target ->
        FilenameRuleEditorDialog(
            target = target,
            settings = settings,
            onDismiss = { editorTarget = null },
            onSave = { rule ->
                state.save(rule)
                editorTarget = null
            },
            onDelete = { rule ->
                editorTarget = null
                deleting = rule
            },
        )
    }

    deleting?.let { rule ->
        DeleteRuleDialog(
            rule = rule,
            onDismiss = { deleting = null },
            onConfirm = {
                state.remove(rule)
                deleting = null
            },
        )
    }
}

/**
 * 一条规则：左边是可点的列表项（点它是编辑规则），右边是开关（只管开关），
 * 中间用一条竖线把两块点击区域分开。
 *
 * 这里没有用 `ExpressiveListItem`：库里的 `ListItem` 会合并子节点的语义，
 * "编辑"和"开关"两个点击区域在无障碍里会被并成一个节点，只能留一个；
 * 用普通 `Row` 拼起来，两块各自是一个焦点，读屏也分得清。
 */
@Composable
private fun FilenameRuleItem(
    index: Int,
    count: Int,
    rule: FilenameRule,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // 行高由内容决定（规则换行时变高），两块点击区域各自撑满整个高度：
                // 点得到的范围和看到的范围才对得上。
                .height(IntrinsicSize.Min)
                .clip(listItemShape(index = index, count = count))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    // 至少和别的列表项一样高，短规则的那几行不会显得比别人矮。
                    .heightIn(min = 72.dp)
                    .fillMaxHeight()
                    .clickable(onClickLabel = "编辑规则", onClick = onEdit)
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // 标题就是规则本身：写长了自动换行，不截成一行省略号。
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "示例：${rule.sampleText()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 竖线就是两块点击区域的交界：固定高度、上下不贴边。
        VerticalDivider(modifier = Modifier.height(RuleDividerHeight))
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "启用「${rule.pattern}」" }
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    // 补在开关两侧：竖线到开关、开关到右边缘都留出一样宽的空隙；
                    // 写在 toggleable 后面，这段空隙也一起属于开关的点击区域。
                    .padding(horizontal = RuleSwitchPadding),
            contentAlignment = Alignment.Center,
        ) {
            // 整块已经能点、也已经是开关语义，Switch 自己不再接点击，免得一个开关出现两个目标。
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

/** 添加/编辑规则的弹窗：写规则串、插入记号，底下实时显示示例或报错。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilenameRuleEditorDialog(
    target: RuleEditorTarget,
    settings: FilenameRuleSettings,
    onDismiss: () -> Unit,
    onSave: (FilenameRule) -> Unit,
    onDelete: (FilenameRule) -> Unit,
) {
    val editing = (target as? RuleEditorTarget.Existing)?.rule
    var pattern by remember { mutableStateOf(TextFieldValue(editing?.pattern.orEmpty())) }
    var error by remember { mutableStateOf<String?>(null) }
    val text = pattern.text
    val invalidReason = FilenamePattern.validate(text)

    fun save() {
        when {
            invalidReason != null -> error = invalidReason
            settings.patternExists(text, excludeId = editing?.id) -> error = "和当前已有的规则重复"
            else ->
                onSave(
                    FilenameRule(
                        id = editing?.id ?: UUID.randomUUID().toString(),
                        pattern = text,
                    ),
                )
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 与卡片、列表项同一套图标容器：弹窗也长得像这个应用的一部分。
                    LeadingIconCircle(icon = if (editing == null) Icons.Rounded.Add else Icons.Rounded.Edit)
                    Text(
                        text = if (editing == null) "添加解析规则" else "编辑解析规则",
                        style = AppTypeScale.dialogTitle,
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        error = null
                    },
                    label = { Text("规则") },
                    placeholder = { Text("yyyy.MM.dd") },
                    singleLine = true,
                    isError = text.isNotEmpty() && invalidReason != null,
                    textStyle = dialogInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                RuleHint(error = error, invalidReason = invalidReason, text = text)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "插入记号",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilenamePattern.tokens.forEach { token ->
                        AssistChip(
                            onClick = {
                                pattern = pattern.insert(token.token)
                                error = null
                            },
                            // 说明在前、记号在后并加粗：用户一眼看到的是含义，要写进规则串的那串字也清楚。
                            label = {
                                Text(
                                    text =
                                        buildAnnotatedString {
                                            append("${token.label} ")
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append(token.token)
                                            }
                                        },
                                    // 主题把 labelLarge 映射成了加粗，这里要的是"说明常规、记号加粗"的对比。
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Normal,
                                        ),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing != null) {
                        TextButton(onClick = { onDelete(editing) }) {
                            Text(
                                text = "删除",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = ::save, enabled = invalidReason == null) {
                        Text("保存", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 弹窗里输入框下面那一行：先报错（写错了或重名），没写错就显示示例。 */
@Composable
private fun RuleHint(
    error: String?,
    invalidReason: String?,
    text: String,
) {
    val message =
        when {
            error != null -> error
            text.isEmpty() -> "写法示例：yyyy.MM.dd"
            invalidReason != null -> invalidReason
            else -> "示例：${FilenamePattern.preview(text, RuleSampleInstant, RuleSampleZone)}"
        }
    val isError = error != null || (text.isNotEmpty() && invalidReason != null)
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}

/** 删除规则前的二次确认：删掉的是列表里的一条规则，删错了要重新写一遍。 */
@Composable
private fun DeleteRuleDialog(
    rule: FilenameRule,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条规则？", style = AppTypeScale.dialogTitle) },
        text = {
            Text(
                "规则：${rule.pattern}\n\n" +
                    "删除后这条规则不再参与解析，需要时可以用右下角的按钮重新添加。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** 分组标题：与设置页、小工具页的排版一致。 */
@Composable
private fun RuleSectionTitle(title: String) {
    Text(
        text = title,
        style = AppTypeScale.sectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = SectionCardCorner, bottom = 8.dp),
    )
}

/** 示例文字：用固定的一刻渲染规则串，用户能直接看懂这种写法认出来的是什么。 */
private fun FilenameRule.sampleText(): String =
    FilenamePattern.preview(pattern, RuleSampleInstant, RuleSampleZone) ?: pattern

/** 把记号插到光标处，插完把光标放到记号后面，方便接着插下一个。 */
private fun TextFieldValue.insert(token: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    return TextFieldValue(
        text = text.replaceRange(start, end, token),
        selection = TextRange(start + token.length),
    )
}
