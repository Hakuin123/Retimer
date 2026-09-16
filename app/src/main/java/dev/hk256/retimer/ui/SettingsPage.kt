package dev.hk256.retimer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.BuildConfig
import dev.hk256.retimer.data.AppLanguage
import dev.hk256.retimer.data.AppSettingsState
import dev.hk256.retimer.data.AppThemeColorMode
import dev.hk256.retimer.data.AppThemeMode
import dev.hk256.retimer.ui.components.ExpressiveListItem
import dev.hk256.retimer.ui.components.ExpressiveSwitchItem
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.SectionCardCorner
import dev.hk256.retimer.ui.theme.AppDarkColorScheme
import dev.hk256.retimer.ui.theme.AppLightColorScheme
import dev.hk256.retimer.ui.theme.AppTypeScale

private const val GITHUB_PROJECT_URL = "https://github.com/Hakuin123/Retimer"
private const val GITHUB_PROFILE_URL = "https://github.com/Hakuin123"
private const val FEEDBACK_URL = "https://github.com/Hakuin123/Retimer/issues"
private const val RELEASES_URL = "https://github.com/Hakuin123/Retimer/releases"

@Composable
internal fun SettingsPage(
    settings: AppSettingsState,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val appearanceItemCount = 4
    val aboutItemCount = 6
    val systemDarkTheme =
        when (settings.themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val systemThemePrimary =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (systemDarkTheme) {
                dynamicDarkColorScheme(context).primary
            } else {
                dynamicLightColorScheme(context).primary
            }
        } else {
            if (systemDarkTheme) AppDarkColorScheme.primary else AppLightColorScheme.primary
        }
    val defaultThemePrimary =
        if (systemDarkTheme) AppDarkColorScheme.primary else AppLightColorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item {
            ScreenHeadline(icon = Icons.Rounded.Settings, title = "设置")
            Spacer(Modifier.height(20.dp))
        }
        item { SettingsSectionTitle("外观") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DropdownSettingItem(
                    index = 0,
                    count = appearanceItemCount,
                    icon = Icons.Rounded.Language,
                    headline = "语言",
                    options = AppLanguage.entries,
                    selected = settings.language,
                    optionLabel = AppLanguage::displayName,
                    onSelect = settings::updateLanguage,
                )
                DropdownSettingItem(
                    index = 1,
                    count = appearanceItemCount,
                    icon = Icons.Rounded.Contrast,
                    headline = "主题",
                    options = AppThemeMode.entries,
                    selected = settings.themeMode,
                    optionLabel = { mode ->
                        when (mode) {
                            AppThemeMode.SYSTEM -> "跟随系统设置"
                            AppThemeMode.LIGHT -> "亮色模式"
                            AppThemeMode.DARK -> "暗色模式"
                        }
                    },
                    onSelect = settings::updateThemeMode,
                )
                DropdownSettingItem(
                    index = 2,
                    count = appearanceItemCount,
                    icon = Icons.Rounded.Palette,
                    headline = "主题颜色",
                    options = AppThemeColorMode.entries,
                    selected = settings.themeColorMode,
                    optionLabel = { mode ->
                        when (mode) {
                            AppThemeColorMode.SYSTEM -> "跟随系统设置"
                            AppThemeColorMode.DEFAULT -> "绿色"
                        }
                    },
                    optionLeadingColor = { mode ->
                        when (mode) {
                            AppThemeColorMode.SYSTEM -> systemThemePrimary
                            AppThemeColorMode.DEFAULT -> defaultThemePrimary
                        }
                    },
                    onSelect = settings::updateThemeColorMode,
                )
                ExpressiveSwitchItem(
                    index = 3,
                    count = appearanceItemCount,
                    icon = Icons.AutoMirrored.Rounded.Label,
                    headline = "隐藏导航按钮标签",
                    supporting = "开启后仅显示选中项的标签",
                    checked = settings.hideUnselectedNavLabels,
                    onCheckedChange = settings::updateHideUnselectedNavLabels,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item { SettingsSectionTitle("关于") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ExpressiveListItem(
                    index = 0,
                    count = aboutItemCount,
                    icon = Icons.Rounded.Info,
                    headline = "版本",
                    supporting = BuildConfig.VERSION_NAME,
                    onClick = {},
                )
                ExpressiveListItem(
                    index = 1,
                    count = aboutItemCount,
                    icon = Icons.Rounded.CalendarToday,
                    headline = "构建日期",
                    supporting = BuildConfig.BUILD_DATE,
                    onClick = {},
                )
                ExternalLinkItem(
                    index = 2,
                    count = aboutItemCount,
                    icon = Icons.Rounded.Code,
                    headline = "GitHub 项目",
                    onClick = { context.openExternalUrl(GITHUB_PROJECT_URL, onShowMessage) },
                )
                ExternalLinkItem(
                    index = 3,
                    count = aboutItemCount,
                    icon = Icons.Rounded.Person,
                    headline = "开发者",
                    supporting = "白隐Hakuin",
                    onClick = { context.openExternalUrl(GITHUB_PROFILE_URL, onShowMessage) },
                )
                ExternalLinkItem(
                    index = 4,
                    count = aboutItemCount,
                    icon = Icons.Rounded.Feedback,
                    headline = "反馈和建议",
                    onClick = { context.openExternalUrl(FEEDBACK_URL, onShowMessage) },
                )
                ExternalLinkItem(
                    index = 5,
                    count = aboutItemCount,
                    icon = Icons.Rounded.SystemUpdate,
                    headline = "检查更新",
                    onClick = { context.openExternalUrl(RELEASES_URL, onShowMessage) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = AppTypeScale.sectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = SectionCardCorner, bottom = 8.dp),
    )
}

@Composable
private fun <T> DropdownSettingItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    headline: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    optionLeadingColor: ((T) -> Color)? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ExpressiveListItem(
            index = index,
            count = count,
            icon = icon,
            headline = headline,
            supporting = optionLabel(selected),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        // DropdownMenu 默认会先尝试左对齐；把 1dp 锚点放在条目右下角后，
        // 原生弹出的右对齐回退位置就会把菜单右边缘与条目右边缘对齐。
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(1.dp),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        leadingIcon =
                            optionLeadingColor?.let { colorFor ->
                                { ThemeColorIndicator(color = colorFor(option)) }
                            },
                        trailingIcon = {
                            if (option == selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "当前选项",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorIndicator(color: Color) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun ExternalLinkItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    headline: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    ExpressiveListItem(
        index = index,
        count = count,
        icon = icon,
        headline = headline,
        supporting = supporting,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun Context.openExternalUrl(
    url: String,
    onShowMessage: (String) -> Unit,
) {
    val opened =
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
    if (!opened) {
        onShowMessage("无法打开链接")
    }
}
