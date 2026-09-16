package dev.hk256.retimer.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * M3 Expressive 标准动效方案（`MotionScheme.standard()`）的弹簧参数。
 *
 * Material 3 1.4.0 尚未公开 `MotionScheme.standard()`（库内部 API），主题因此沿用
 * [androidx.compose.material3.MaterialTheme] 的默认动效方案——它就是标准方案，
 * 组件动效与自定义动画都使用同一组曲线；阻尼比 0.9 ~ 1.0，只调整刚度，因此不回弹。
 *
 * 数值与 `StandardMotionTokens` 一致，用法与库内组件保持一致：
 * - 位置/尺寸变化用 spatial（进入用 default，退出用 fast）
 * - 颜色/透明度变化用 effects（进入用 default，退出用 fast）
 */
internal object AppMotion {
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}
