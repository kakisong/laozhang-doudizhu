package com.kaynzhang.doudizhu

import com.kaynzhang.doudizhu.data.Room

/**
 * A table requested by a test harness through intent extras (debug builds only; see DebugHooks).
 * [scenario] names a scripted deal, [autoplay] turns on 托管 for the human, [loop] keeps playing,
 * [turbo] removes pacing and most animation time.
 */
data class DebugLaunch(
    val room: Room,
    val seed: Long?,
    val scenario: String?,
    val autoplay: Boolean,
    val loop: Boolean,
    val turbo: Boolean = false,
)
