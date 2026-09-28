package com.stamina.helper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主色调：白 + 灰 + 浅蓝
 * 浅色模式下整体偏白，深色模式为白字深底
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF333333),           // 主色：深灰
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF6B9BD1),         // 辅色：浅蓝
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8F0FA),
    onSecondaryContainer = Color(0xFF1A2A3D),
    tertiary = Color(0xFF7EB8A5),          // 浅青点缀
    background = Color(0xFFFAFAFA),        // 白调背景
    onBackground = Color(0xFF11151C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11151C),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5B6675),
    outline = Color(0xFFD0D0D0),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0E0E0),           // 亮灰白
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color(0xFFE8E8E8),
    secondary = Color(0xFF8FB8E8),         // 亮浅蓝
    onSecondary = Color(0xFF06213F),
    secondaryContainer = Color(0xFF1E2A38),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFF5FBFA8),
    background = Color(0xFF0A0F0C),
    onBackground = Color(0xFFF0F5F2),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF0F5F2),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF9AA6A0),
    outline = Color(0xFF333333),
    error = Color(0xFFFF6B6E),
    onError = Color(0xFF3B0A0B),
)

/** 品牌色，供非 Material 组件使用 */
object BrandColors {
    val Green = Color(0xFF333333)
    val GreenLight = Color(0xFF666666)
    val Blue = Color(0xFF6B9BD1)
    val BlueLight = Color(0xFF8FB8E8)
    val White = Color(0xFFFFFFFF)
    val GlassGreen = Color(0x66333333)
    val GlassBlue = Color(0x666B9BD1)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}