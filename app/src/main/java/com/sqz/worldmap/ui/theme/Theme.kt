package com.sqz.worldmap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6B57),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1F1D5),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    surface = Color(0xFFF7FBF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF3F4944),
    background = Color(0xFFF7FBF7),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF95D4BA),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF15503C),
    onPrimaryContainer = Color(0xFFB1F1D5),
    secondary = Color(0xFFB3CCC0),
    surface = Color(0xFF111816),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    background = Color(0xFF0B1210),
)

@Composable
fun WorldMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

@Immutable
data class GlobePalette(
    val oceanCenter: Color,
    val oceanEdge: Color,
    val rim: Color,
    /** 陆地配色（按 ISO 哈希取色，让相邻国家颜色不同）。 */
    val land: List<Color>,
    /** 国界描边颜色（与陆地填充有足够对比）。 */
    val border: Color,
    val selectedFill: Color,
    val selectedStroke: Color,
    val cityDot: Color,
    val capitalDot: Color,
    val label: Color,
    /** 标签描边光晕颜色（与 label 反相，保证在陆地/海洋上都可读）。 */
    val labelHalo: Color,
    val capitalLabel: Color,
)

val LightGlobePalette = GlobePalette(
    oceanCenter = Color(0xFFE3F0FA),
    oceanEdge = Color(0xFFAACEE5),
    rim = Color(0xFF7FB4D2),
    land = listOf(
        Color(0xFFAFC6A5),
        Color(0xFFC9C6A0),
        Color(0xFFA9C2BE),
        Color(0xFFD3C2A6),
        Color(0xFFB7C99C),
        Color(0xFFC6BFA6),
        Color(0xFFB4C8B6),
        Color(0xFFCFC5B0),
    ),
    border = Color(0xFF46564C),
    selectedFill = Color(0x73E9A33D),
    selectedStroke = Color(0xFFB4771B),
    cityDot = Color(0xFF5C6B63),
    capitalDot = Color(0xFFB4771B),
    label = Color(0xFF1F2B24),
    labelHalo = Color(0xFFFFFDF8),
    capitalLabel = Color(0xFF7A4E00),
)

val DarkGlobePalette = GlobePalette(
    oceanCenter = Color(0xFF153D52),
    oceanEdge = Color(0xFF05161F),
    rim = Color(0xFF4E93B8),
    land = listOf(
        Color(0xFF3F6F57),
        Color(0xFF4C6E62),
        Color(0xFF61704D),
        Color(0xFF556B5E),
        Color(0xFF46705F),
        Color(0xFF5C6F53),
        Color(0xFF4B6B60),
        Color(0xFF5A6C5E),
    ),
    border = Color(0xFF9DBBAD),
    selectedFill = Color(0x8CF4C25B),
    selectedStroke = Color(0xFFFFD166),
    cityDot = Color(0xFFCBD8D1),
    capitalDot = Color(0xFFFFD166),
    label = Color(0xFFF3F8F5),
    labelHalo = Color(0xFF07120D),
    capitalLabel = Color(0xFFFFE0A3),
)

@Composable
fun globePalette(darkTheme: Boolean = isSystemInDarkTheme()): GlobePalette =
    if (darkTheme) DarkGlobePalette else LightGlobePalette
