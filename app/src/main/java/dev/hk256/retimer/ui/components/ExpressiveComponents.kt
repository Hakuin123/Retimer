package dev.hk256.retimer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hk256.retimer.ui.theme.AppMotion

/** 列表成组时的圆角位置：外侧 28dp，相邻内侧 8dp。 */
internal fun listItemShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 8.dp): Shape =
    when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 ->
            RoundedCornerShape(
                topStart = outer,
                topEnd = outer,
                bottomStart = inner,
                bottomEnd = inner,
            )
        index == count - 1 ->
            RoundedCornerShape(
                topStart = inner,
                topEnd = inner,
                bottomStart = outer,
                bottomEnd = outer,
            )
        else -> RoundedCornerShape(inner)
    }

/**
 * 分组卡片底板的圆角，以及内容相对底板的内边距。
 *
 * [SectionCardItemCorner] 是给分组里最外侧选项用的：内层圆角取「外圆角 - 内边距」才能和
 * 底板同心，否则最下面一项的圆角会明显偏离底板轮廓（这两个值必须一起改，所以放在一处）。
 */
internal val SectionCardCorner = 28.dp
internal val SectionCardPadding = 3.dp
internal val SectionCardItemCorner = SectionCardCorner - SectionCardPadding

/** 可点击表面：按压缩放 + 涟漪反馈，并把涟漪裁剪在给定形状内。 */
@Composable
internal fun Modifier.pressableSurface(
    shape: Shape,
    containerColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        // 按下缩放来自产品规范（库内无等价实现，只提供涟漪/状态层），曲线取自标准方案的 fast spatial。
        animationSpec = AppMotion.fastSpatial(),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(containerColor)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
}

/** 40dp 的图标圆形容器，默认使用 primaryContainer 角色。 */
@Composable
internal fun LeadingIconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

/** 页面大标题：图标 + headlineMediumEmphasized。 */
@Composable
internal fun ScreenHeadline(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadingIconCircle(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 填充信息卡片：20dp 圆角、20dp 内边距。
 *
 * 全应用共用这一种卡片样式，所以**不开放任何视觉参数**——图标底色、图标颜色、标题字号、
 * 行距都写死在这里。每处都传参数的话，几张卡很快就会各自长歪；要改样式就改这一处。
 *
 * 配色用 primaryContainer / onPrimaryContainer：这是 M3 的标准容器配对（容器 tone 90、
 * 其上文字 tone 10，明度差 80），底色比 surfaceContainerHighest 更有色彩倾向，与页面底色
 * （surface，tone 98）区分更明显，正文与标题都保持 80 的差值。
 * 图标则用填充式 primary 底 + onPrimary 图标，作为卡片内的视觉焦点。
 *
 * 标题字号 18sp 是显式给的：M3 字号表里 16sp（titleMedium）和 22sp（titleLarge）之间没有
 * 角色，而标题只需要比正文（bodyMedium 14sp）明显大一点，22sp 又过大。
 */
@Composable
internal fun FilledInfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconCircle(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        // 标题（含图标）与介绍文本之间留出稍大的间距，两行内容不会挤在一起。
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * M3 Expressive 列表项：72dp 高、40dp primaryContainer 圆形图标、bodyLarge 主文本、
 * bodyMedium 辅助文本，背景使用指定颜色角色（默认 surfaceContainerLow）。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ExpressiveListItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    headline: String,
    supporting: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cornerRadius: Dp = 28.dp,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    leadingIconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    leadingIconOffset: Dp = 0.dp,
    contentOffset: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = listItemShape(index = index, count = count, outer = cornerRadius)
    val rowModifier =
        if (onClick != null) {
            Modifier.pressableSurface(shape = shape, containerColor = containerColor, enabled = enabled, onClick = onClick)
        } else {
            Modifier.clip(shape).background(containerColor)
        }
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).then(rowModifier),
        headlineContent = {
            Text(
                text = headline,
                style = headlineStyle,
                color = headlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(x = contentOffset),
            )
        },
        supportingContent =
            supporting?.let { text ->
                {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset(x = contentOffset),
                    )
                }
            },
        leadingContent = {
            LeadingIconCircle(
                icon = icon,
                modifier = Modifier.offset(x = leadingIconOffset),
                containerColor = leadingIconContainerColor,
                contentColor = leadingIconContentColor,
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
    )
}

/** 带开关的列表项：整行可点击切换，右侧为标准 Switch。 */
@Composable
internal fun ExpressiveSwitchItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    headline: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cornerRadius: Dp = 28.dp,
    leadingIconOffset: Dp = 0.dp,
    contentOffset: Dp = 0.dp,
) {
    ExpressiveListItem(
        index = index,
        count = count,
        icon = icon,
        headline = headline,
        supporting = supporting,
        containerColor = containerColor,
        cornerRadius = cornerRadius,
        leadingIconOffset = leadingIconOffset,
        contentOffset = contentOffset,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/**
 * 抽屉式分组卡片：标题行所在的卡片会随展开一起变高，选项从卡片内部向下"抽"出来，
 * 所以下面的选项看起来是分组标题的子项。
 *
 * [containerColor] 默认是中性容器色；需要让整个分组"带点颜色"时传入彩色容器角色，
 * 标题文字会自动取该角色的前景色。
 */
@Composable
internal fun ExpandingSectionCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = AppMotion.fastSpatial(),
        label = "sectionPressScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        // 旋转属于空间变化，使用标准方案的 default spatial（库内无对应组件可比对）。
        animationSpec = AppMotion.defaultSpatial(),
        label = "sectionChevron",
    )
    // 展开后标题行底部圆角收窄，读起来像卡片被向下拉开
    val headerShape =
        if (expanded) {
            RoundedCornerShape(
                topStart = SectionCardCorner,
                topEnd = SectionCardCorner,
                bottomStart = 8.dp,
                bottomEnd = 8.dp,
            )
        } else {
            RoundedCornerShape(SectionCardCorner)
    }
    val contentColor =
        contentColorFor(containerColor).takeIf { it != Color.Unspecified }
            ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(SectionCardCorner))
            .background(containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onToggle,
                )
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingIconCircle(
                icon = icon,
                modifier = Modifier.offset(x = 4.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            // 展开箭头用实心主色：在彩色卡片上仍然是一个明确的可点指示。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(AppMotion.defaultEffects()) + fadeIn(AppMotion.defaultEffects()),
            exit = shrinkVertically(AppMotion.defaultEffects()) + fadeOut(AppMotion.fastEffects()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SectionCardPadding),
                verticalArrangement = Arrangement.spacedBy(SectionCardPadding),
                content = content,
            )
        }
    }
}
