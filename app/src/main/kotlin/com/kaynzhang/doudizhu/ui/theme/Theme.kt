package com.kaynzhang.doudizhu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object DdzColors {
    val FeltCenter = Color(0xFF234C41)
    val FeltEdge = Color(0xFF102B25)
    val FeltLine = Color(0x24D8C49B)

    val Gold = Color(0xFFE4C994)
    val GoldDeep = Color(0xFFAF8D54)
    val Cream = Color(0xFFF5F0E5)
    val TextMuted = Color(0xFFADC1B7)
    val Hairline = Color(0x33D8C49B)
    val Ink = Color(0xFF24362E)
    val Surface = Color(0xFF1B3931)

    val CardFace = Color(0xFFFFFCF4)
    val CardBorder = Color(0xFFC9BEA6)
    val SuitRed = Color(0xFFAD303E)
    val SuitBlack = Color(0xFF23342E)
    val BackRed = Color(0xFF853441)
    val BackRedDeep = Color(0xFF4F202D)

    // Legacy names remain for callers; actions now share a restrained, accessible palette.
    val Orange = Color(0xFFE4C994)
    val OrangeDeep = Color(0xFFD4B57C)
    val Blue = Color(0xFF2B5044)
    val BlueDeep = Color(0xFF244238)
    val Green = Color(0xFFE4C994)
    val GreenDeep = Color(0xFFD4B57C)
    val Gray = Color(0xFF203C33)
    val GrayDeep = Color(0xFF193229)

    val Panel = Color(0xB31A352D)
    val PanelStrong = Color(0xFF162F28)
    val Scrim = Color(0xBB081510)
    val Win = Gold
    val Lose = TextMuted

    val felt: Brush
        get() = Brush.radialGradient(listOf(FeltCenter, FeltEdge))
}

private val scheme = darkColorScheme(
    primary = DdzColors.Orange,
    onPrimary = DdzColors.Ink,
    secondary = DdzColors.Gold,
    onSecondary = DdzColors.Ink,
    background = DdzColors.FeltEdge,
    surface = DdzColors.PanelStrong,
    onSurface = DdzColors.Cream,
    onBackground = DdzColors.Cream,
)

@Composable
fun DdzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
