package com.kaynzhang.doudizhu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object DdzColors {
    val FeltCenter = Color(0xFF1E7B55)
    val FeltEdge = Color(0xFF0A4430)
    val FeltLine = Color(0x33FFFFFF)

    val Gold = Color(0xFFF6C453)
    val GoldDeep = Color(0xFFC8962B)
    val Cream = Color(0xFFFFF8E7)

    val CardFace = Color(0xFFFFFDF7)
    val CardBorder = Color(0xFFD6CCB6)
    val SuitRed = Color(0xFFD32F2F)
    val SuitBlack = Color(0xFF1F1F1F)
    val BackRed = Color(0xFFB3261E)
    val BackRedDeep = Color(0xFF7A1712)

    val Orange = Color(0xFFF59E0B)
    val OrangeDeep = Color(0xFFD97706)
    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF1D4ED8)
    val Green = Color(0xFF22C55E)
    val GreenDeep = Color(0xFF15803D)
    val Gray = Color(0xFF9CA3AF)
    val GrayDeep = Color(0xFF4B5563)

    val Panel = Color(0xB3081F17)
    val PanelStrong = Color(0xE6071A13)
    val Scrim = Color(0x99000000)
    val Win = Color(0xFFFFD54F)
    val Lose = Color(0xFF90A4AE)

    val felt: Brush
        get() = Brush.radialGradient(listOf(FeltCenter, FeltEdge))
}

private val scheme = darkColorScheme(
    primary = DdzColors.Orange,
    onPrimary = Color.White,
    secondary = DdzColors.Blue,
    background = DdzColors.FeltEdge,
    surface = DdzColors.PanelStrong,
    onSurface = DdzColors.Cream,
    onBackground = DdzColors.Cream,
)

@Composable
fun DdzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
