package com.kaynzhang.doudizhu

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaynzhang.doudizhu.data.BuiltInPortraits
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.game.HUMAN
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual picker, persisted profile, and human seat wiring as one user flow. */
@RunWith(AndroidJUnit4::class)
class PortraitSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) = rule.waitUntil(20_000) { exists(tag) }

    @Test
    fun aChosenPortraitIsSavedAndFollowsThePlayerIntoANewTable() {
        waitFor("btn_settings")
        lateinit var vm: GameViewModel
        rule.runOnIdle {
            vm = ViewModelProvider(rule.activity)[GameViewModel::class.java]
            // Ensure the picker changes the value even if a previous run selected the same portrait.
            vm.setAvatar(BuiltInPortraits.DEFAULT_ID)
        }
        rule.waitUntil(20_000) { vm.appData.value?.profile?.avatarId == BuiltInPortraits.DEFAULT_ID }
        val before = rule.runOnIdle { vm.appData.value!! }

        rule.onNodeWithTag("btn_settings").performClick()
        waitFor("btn_choose_portrait")
        rule.onNodeWithTag("btn_choose_portrait").performClick()
        waitFor("portrait_li_ayi")
        rule.onNodeWithTag("portrait_li_ayi").performClick()

        rule.waitUntil(20_000) {
            vm.appData.value?.profile?.avatarId == "li_ayi" && !exists("btn_close_portraits")
        }
        rule.runOnIdle {
            val saved = vm.appData.value!!
            assertEquals("li_ayi", saved.profile.avatarId)
            assertEquals("Changing a portrait must preserve coins", before.profile.coins, saved.profile.coins)
            assertEquals("Changing a portrait must preserve the player's record", before.stats, saved.stats)
        }

        rule.onNodeWithTag("btn_page_back").performClick()
        waitFor("room_novice")
        rule.onNodeWithTag("room_novice").performClick()
        rule.waitUntil(20_000) { vm.table.value?.seats?.get(HUMAN)?.avatar == "li_ayi" }
        rule.runOnIdle {
            assertEquals("The table must use the saved player identity", "li_ayi", vm.table.value!!.seats[HUMAN].avatar)
        }
    }
}
