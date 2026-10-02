package com.kaynzhang.doudizhu.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/** Quiet feedback for shared buttons and navigation, supplied by the app's audio controller. */
val LocalUiSound = staticCompositionLocalOf<() -> Unit> { {} }
