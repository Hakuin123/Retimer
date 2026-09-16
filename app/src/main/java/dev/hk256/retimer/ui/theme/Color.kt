package dev.hk256.retimer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Material 3 配色角色定义。
 *
 * 这里是唯一允许出现十六进制色值的地方；界面代码必须通过角色名
 * （primary、surfaceContainer、onSurfaceVariant 等）引用颜色。
 */

private val Primary = Color(0xFF2E6A45)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFB0F1C2)
private val OnPrimaryContainer = Color(0xFF00210F)

private val Secondary = Color(0xFF516356)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFD3E8D8)
private val OnSecondaryContainer = Color(0xFF102016)

private val Tertiary = Color(0xFF3D6373)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFC2E8FF)
private val OnTertiaryContainer = Color(0xFF001E2C)

private val Error = Color(0xFFB3261E)
private val OnError = Color(0xFFFFFFFF)
private val ErrorContainer = Color(0xFFF9DEDC)
private val OnErrorContainer = Color(0xFF410E0B)

private val Surface = Color(0xFFF6FBF4)
private val OnSurface = Color(0xFF181D18)
private val SurfaceContainerLowest = Color(0xFFFFFFFF)
private val SurfaceContainerLow = Color(0xFFF0F5EE)
private val SurfaceContainer = Color(0xFFEAF0E8)
private val SurfaceContainerHigh = Color(0xFFE4EAE2)
private val SurfaceContainerHighest = Color(0xFFDEE4DC)
private val SurfaceBright = Color(0xFFFAFFF8)
private val SurfaceDim = Color(0xFFD6DCD5)

private val OnSurfaceVariant = Color(0xFF414941)
private val Outline = Color(0xFF707972)
private val OutlineVariant = Color(0xFFBFC9C0)

private val InverseSurface = Color(0xFF2D322D)
private val InverseOnSurface = Color(0xFFEEF2EB)
private val InversePrimary = Color(0xFF95D5A7)

private val Scrim = Color(0xFF000000)

private val DarkPrimary = Color(0xFF96D5A9)
private val DarkOnPrimary = Color(0xFF00391C)
private val DarkPrimaryContainer = Color(0xFF12512E)
private val DarkOnPrimaryContainer = Color(0xFFB1F1C5)

private val DarkSecondary = Color(0xFFB7CBBD)
private val DarkOnSecondary = Color(0xFF223529)
private val DarkSecondaryContainer = Color(0xFF3A4B3F)
private val DarkOnSecondaryContainer = Color(0xFFD3E8D8)

private val DarkTertiary = Color(0xFF8ECFE4)
private val DarkOnTertiary = Color(0xFF003641)
private val DarkTertiaryContainer = Color(0xFF1E4D54)
private val DarkOnTertiaryContainer = Color(0xFFBBEBF2)

private val DarkError = Color(0xFFF2B8B5)
private val DarkOnError = Color(0xFF601410)
private val DarkErrorContainer = Color(0xFF8C1D18)
private val DarkOnErrorContainer = Color(0xFFF9DEDC)

private val DarkSurface = Color(0xFF101411)
private val DarkOnSurface = Color(0xFFDEE4E0)
private val DarkSurfaceContainerLowest = Color(0xFF0A0E0B)
private val DarkSurfaceContainerLow = Color(0xFF181C1A)
private val DarkSurfaceContainer = Color(0xFF1C211E)
private val DarkSurfaceContainerHigh = Color(0xFF272B28)
private val DarkSurfaceContainerHighest = Color(0xFF313633)
private val DarkSurfaceBright = Color(0xFF353A36)
private val DarkSurfaceDim = Color(0xFF0B0F0C)

private val DarkOnSurfaceVariant = Color(0xFFBDCAC0)
private val DarkOutline = Color(0xFF87948B)
private val DarkOutlineVariant = Color(0xFF3E4941)

private val DarkInverseSurface = Color(0xFFDEE4E0)
private val DarkInverseOnSurface = Color(0xFF2D312E)
private val DarkInversePrimary = Color(0xFF2E6A45)

private val DarkScrim = Color(0xFF000000)

/*
 * 警告色。
 *
 * M3 的 ColorScheme 里只有 error（"出错了"），没有表达"有内容处理不了、但不算失败"的角色，
 * 所以这里补一个黄橙色。ColorScheme 是封闭的、不能直接加槽位，因此它通过 Theme.kt 里的
 * CompositionLocal 暴露，用法与标准角色一致：MaterialTheme.colorScheme.warning。
 *
 * 取值偏暗（对 surface 的对比度约 4.3:1）是为了在浅色底上作为正文级文字仍然可读。
 * 这个角色只作前景色用在 surface 上，所以没有配套的 onWarning。
 */
private val Warning = Color(0xFFA56600)

internal val AppWarningColor: Color = Warning
internal val AppDarkWarningColor: Color = Color(0xFFFFB95C)

internal val AppLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        inversePrimary = InversePrimary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = OnSecondaryContainer,
        tertiary = Tertiary,
        onTertiary = OnTertiary,
        tertiaryContainer = TertiaryContainer,
        onTertiaryContainer = OnTertiaryContainer,
        error = Error,
        onError = OnError,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        background = Surface,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceContainerHighest,
        onSurfaceVariant = OnSurfaceVariant,
        surfaceTint = Primary,
        surfaceBright = SurfaceBright,
        surfaceDim = SurfaceDim,
        surfaceContainerLowest = SurfaceContainerLowest,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        inverseSurface = InverseSurface,
        inverseOnSurface = InverseOnSurface,
        outline = Outline,
        outlineVariant = OutlineVariant,
        scrim = Scrim,
    )

internal val AppDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = DarkInversePrimary,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkSurface,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceContainerHighest,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = DarkPrimary,
        surfaceBright = DarkSurfaceBright,
        surfaceDim = DarkSurfaceDim,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = DarkScrim,
    )
