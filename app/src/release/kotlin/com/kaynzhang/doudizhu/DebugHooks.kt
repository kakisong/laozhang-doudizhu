package com.kaynzhang.doudizhu

import android.content.Intent
import androidx.compose.ui.Modifier
import com.kaynzhang.doudizhu.game.GameViewModel

/** Release builds ignore test-harness intents. */
object DebugHooks {
    val rootModifier: Modifier = Modifier

    fun parse(intent: Intent?): DebugLaunch? = null

    fun start(vm: GameViewModel, launch: DebugLaunch): Boolean = false
}
