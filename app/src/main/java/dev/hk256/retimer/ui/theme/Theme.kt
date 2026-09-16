package dev.hk256.retimer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import dev.hk256.retimer.data.AppThemeColorMode
import dev.hk256.retimer.data.AppThemeMode

/**
 * 配色方案里 ColorScheme 没有的角色。
 *
 * M3 的 [ColorScheme] 槽位是固定的，自定义角色只能另开一份数据并通过 CompositionLocal 下发，
 * 否则界面代码要么写死色值、要么绕开角色名，两者都违反"颜色只通过角色名引用"的约定。
 */
@Immutable
internal data class ExtraColors(
    /** "有内容处理不了、但不算失败"的提示色，语义轻于 error。 */
    val warning: Color,
)

private val LightExtraColors = ExtraColors(warning = AppWarningColor)
private val DarkExtraColors = ExtraColors(warning = AppDarkWarningColor)

private val LocalExtraColors = staticCompositionLocalOf { LightExtraColors }

/**
 * 警告色。
 *
 * 用法与标准角色完全一致：`MaterialTheme.colorScheme.warning`。
 * 加在这里而不是直接暴露常量，是为了将来引入深色配色时不必改调用点。
 */
val ColorScheme.warning: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalExtraColors.current.warning

/**
 * 应用主题。
 *
 * [MaterialTheme] 的默认动效方案就是 `MotionScheme.standard()`（不回弹的标准弹簧曲线），
 * 因此这里只覆盖配色与字体，动效保持标准方案。
 */
@Composable
fun MediaTimeFixerTheme(
    themeMode: AppThemeMode,
    themeColorMode: AppThemeColorMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme =
        when (themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val colorScheme =
        when {
            themeColorMode == AppThemeColorMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> AppDarkColorScheme
            else -> AppLightColorScheme
        }
    val view = LocalView.current

    // 勾选亮/暗主题后，系统栏图标也要跟着应用实际使用的主题切换，
    // 否则强制暗色而系统仍为亮色时，状态栏图标会与背景同色。
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
