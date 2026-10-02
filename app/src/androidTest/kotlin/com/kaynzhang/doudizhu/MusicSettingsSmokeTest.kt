package com.kaynzhang.doudizhu

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaynzhang.doudizhu.game.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The settings row must mute only music, retain its volume, and remain usable after recreation. */
@RunWith(AndroidJUnit4::class)
class MusicSettingsSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun musicSwitchAndVolumePersistIndependentlyOfEffectsAndVoice() {
        waitFor("btn_settings")
        lateinit var vm: GameViewModel
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        val previous = rule.runOnIdle { vm.appData.value!!.settings }
        val initial = previous.copy(music = true, musicVolume = 25, sound = false, voice = false,
            soundVolume = 35, voiceVolume = 55)
        try {
            rule.runOnIdle { vm.updateSettings { initial } }
            rule.waitUntil(20_000) { vm.appData.value?.settings == initial }
            rule.onNodeWithTag("btn_settings").performClick()
            waitFor("switch_music")
            rule.onNodeWithTag("switch_music").performScrollTo().assertIsOn().performClick()
            rule.waitUntil(20_000) { vm.appData.value?.settings?.music == false }
            rule.onNodeWithTag("slider_music_volume").performScrollTo().assertIsNotEnabled()
            rule.runOnIdle {
                assertEquals("Muting music must preserve every other preference and its stored volume",
                    initial.copy(music = false), vm.appData.value!!.settings)
            }

            rule.onNodeWithTag("switch_music").performScrollTo().performClick()
            rule.waitUntil(20_000) { vm.appData.value?.settings?.music == true }
            rule.onNodeWithTag("slider_music_volume").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(40f) }
            val changed = initial.copy(musicVolume = 40)
            rule.waitUntil(20_000) { vm.appData.value?.settings == changed }

            rule.activityRule.scenario.recreate()
            waitFor("switch_music")
            rule.onNodeWithTag("switch_music").performScrollTo().assertIsOn()
            rule.onNodeWithTag("slider_music_volume").performScrollTo().assertIsEnabled()
            rule.runOnIdle {
                assertEquals("Recreation must preserve the independently saved music setting",
                    changed, vm.appData.value!!.settings)
            }
        } finally {
            rule.runOnIdle { vm.updateSettings { previous } }
            rule.waitUntil(20_000) { vm.appData.value?.settings == previous }
        }
    }

    private fun waitFor(tag: String) = rule.waitUntil(20_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}
