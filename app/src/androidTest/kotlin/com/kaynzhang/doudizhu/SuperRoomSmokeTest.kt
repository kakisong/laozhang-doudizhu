package com.kaynzhang.doudizhu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.screens.LobbyRooms
import com.kaynzhang.doudizhu.ui.theme.DdzTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The fourth room must remain reachable with complete labels on short phones and larger fonts. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SuperRoomSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun allRoomsAreReadableAndReachableAt640By320WithLargerText() {
        verifyShortLobby(DpSize(640.dp, 320.dp), DpSize(416.dp, 234.dp), coins = 10_000)
    }

    @Test
    fun allRoomsAreReadableAndReachableAt800By360WithLargerText() {
        verifyShortLobby(DpSize(800.dp, 360.dp), DpSize(486.dp, 274.dp), coins = 30_000)
    }

    @Test
    fun wideLobbyShowsAllFourRoomsWithoutScrolling() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 380.dp))) {
                DdzTheme {
                    Box(Modifier.fillMaxSize()) {
                        LobbyRooms(30_000, compact = false, short = false, onEnterRoom = {},
                            modifier = Modifier.size(800.dp, 320.dp))
                    }
                }
            }
        }
        for (room in Room.entries) {
            rule.onNodeWithTag("room_${room.name.lowercase()}")
                .assertIsDisplayed().assertWidthIsAtLeast(176.dp).assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("room_scroll_hint").assertDoesNotExist()
    }

    private fun verifyShortLobby(screen: DpSize, rooms: DpSize, coins: Long) {
        var chosen: Room? = null
        rule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(screen) then DeviceConfigurationOverride.FontScale(1.3f),
            ) {
                DdzTheme {
                    Box(Modifier.fillMaxSize()) {
                        LobbyRooms(coins, compact = screen.width < 760.dp, short = true,
                            onEnterRoom = { chosen = it }, modifier = Modifier.size(rooms))
                    }
                }
            }
        }
        rule.onNodeWithTag("room_scroll_hint").assertIsDisplayed()
        for (room in Room.entries) {
            val tag = "room_${room.name.lowercase()}"
            val card = rule.onNodeWithTag(tag).performScrollTo()
                .assertIsDisplayed().assertWidthIsAtLeast(176.dp).assertHeightIsAtLeast(48.dp)
            val ancestor = hasAnyAncestor(hasTestTag(tag))
            val title = assertTextFits(hasText(room.title) and ancestor)
            val score = assertTextFits(hasText(formatCoins(room.baseScore)) and ancestor)
            val opponent = assertTextFits(hasText(if (room == Room.SUPER) "对手 · 离线 AI" else "对手 · ${room.difficulty.zh}") and ancestor)
            val entry = assertTextFits(hasText(if (coins >= room.minCoins) "入场 ${formatCoins(room.minCoins)}" else "需 ${formatCoins(room.minCoins)} 金币") and ancestor)
            assertTrue("${room.title}: heading must not overlap the base score", title.bottom <= score.top)
            assertTrue("${room.title}: score must not overlap the difficulty", score.bottom <= opponent.top)
            assertTrue("${room.title}: difficulty must not overlap the entry requirement", opponent.bottom <= entry.top)
            card.performClick()
            rule.runOnIdle { assertEquals(room, chosen) }
        }
        rule.onNode(hasContentDescription("超级场，离线 AI对手，底分 2000，入场需要 20000 金币"))
            .assertIsDisplayed()
    }

    private fun assertTextFits(matcher: SemanticsMatcher): androidx.compose.ui.geometry.Rect {
        val node = rule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Text must expose its rendered layout", layouts.isNotEmpty())
        for (layout in layouts) {
            assertFalse(
                "Room text '${layout.layoutInput.text}' must fit: " +
                    "width overflow=${layout.didOverflowWidth}, height overflow=${layout.didOverflowHeight}, " +
                    "constraints=${layout.layoutInput.constraints}, rendered size=${layout.size}, " +
                    "paragraph width=${layout.multiParagraph.width}, line right=${layout.getLineRight(0)}",
                layout.hasVisualOverflow,
            )
        }
        return node.fetchSemanticsNode().boundsInRoot
    }
}
