package dev.hk256.retimer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * 字体使用 Roboto（Android 平台的 FontFamily.Default 即 Roboto）。
 *
 * 标题、按钮文字和标签页使用 M3 Expressive 的 emphasized 字体样式（headlineMediumEmphasized、
 * titleLargeEmphasized、labelLargeEmphasized 等），即同一字号下更粗的字重。Material 3 1.4.0 只在
 * 库内部暴露 emphasized 样式（Typography 的 emphasized 构造参数与属性都是库内部 API），因此这里
 * 把这些角色映射为同名角色的更粗字重，界面直接引用标准角色即可。
 */

private val BaseTypography = Typography()

private fun TextStyle.emphasized(): TextStyle =
    copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)

/**
 * 明确要求常规字重的角色。
 *
 * 主题把标题、按钮、标签页等角色映射成了 emphasized（更粗），但顶栏标题、页面大标题、
 * 分组标题这类文案仍然用标准字重，这里保留一份未加粗的标准角色供它们使用。
 */
internal object AppTypeScale {
    /** 分组标题（如"选项"）。 */
    val sectionTitle: TextStyle = BaseTypography.titleMedium

    /**
     * 对话框标题。
     *
     * headlineSmall 在主题里被映射成了 emphasized（更粗），但对话框标题不需要那么重，
     * 这里保留同字号的常规字重。
     */
    val dialogTitle: TextStyle = BaseTypography.headlineSmall

    /**
     * 标签页文字。
     *
     * titleSmall 在主题里被映射成了 emphasized（更粗），标签页不需要那么重，
     * 这里保留同字号的常规字重。
     */
    val tabLabel: TextStyle = BaseTypography.titleSmall
}

internal val AppTypography: Typography =
    Typography(
        displayLarge = BaseTypography.displayLarge,
        displayMedium = BaseTypography.displayMedium,
        displaySmall = BaseTypography.displaySmall,
        // 顶栏标题、页面大标题保持标准字重；卡片标题等使用 emphasized（更粗）字重
        headlineLarge = BaseTypography.headlineLarge.emphasized(),
        headlineMedium = BaseTypography.headlineMedium,
        headlineSmall = BaseTypography.headlineSmall.emphasized(),
        titleLarge = BaseTypography.titleLarge,
        titleMedium = BaseTypography.titleMedium.emphasized(),
        titleSmall = BaseTypography.titleSmall.emphasized(),
        // 正文保持常规字重
        bodyLarge = BaseTypography.bodyLarge,
        bodyMedium = BaseTypography.bodyMedium,
        bodySmall = BaseTypography.bodySmall,
        // 按钮与标签文字使用 emphasized（更粗）字重
        labelLarge = BaseTypography.labelLarge.emphasized(),
        labelMedium = BaseTypography.labelMedium,
        labelSmall = BaseTypography.labelSmall,
    )
