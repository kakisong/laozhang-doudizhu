package com.kaynzhang.doudizhu.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.data.BuiltInPortraits
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.PlayerPortrait
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import com.kaynzhang.doudizhu.ui.common.LocalUiSound

/** Pick an identity once; the same portrait follows the player into the table and settlement. */
@Composable
fun PortraitPicker(selectedId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val clickSound = LocalUiSound.current
    BackHandler(onBack = onDismiss)
    Overlay {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Panel(Modifier.widthIn(max = 820.dp).fillMaxWidth().heightIn(max = maxHeight)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("选择形象", color = DdzColors.Cream, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
                            Text("${BuiltInPortraits.all.size} 款形象，点击使用，可上下滑动", color = DdzColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        Box(
                            Modifier.size(48.dp).testTag("btn_close_portraits")
                                .semantics { contentDescription = "关闭形象选择" }
                                .clickable(role = Role.Button, onClick = { clickSound(); onDismiss() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(UiIcon.CLOSE, Modifier.size(21.dp), DdzColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                        val columns = when {
                            maxWidth >= 640.dp -> 8
                            maxWidth >= 460.dp -> 6
                            else -> 4
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxWidth().testTag("portrait_grid"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(BuiltInPortraits.all, key = { it.id }) { portrait ->
                                val selected = portrait.id == selectedId
                                val shape = RoundedCornerShape(12.dp)
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(if (selected) DdzColors.Gold.copy(alpha = 0.12f) else Color.Transparent, shape)
                                        .border(1.dp, if (selected) DdzColors.Gold else DdzColors.Hairline, shape)
                                        .selectable(selected = selected, role = Role.RadioButton, onClick = { clickSound(); onSelect(portrait.id) })
                                        .testTag("portrait_${portrait.id}")
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    PlayerPortrait(portrait.id, Modifier.size(48.dp))
                                    Text(portrait.name, color = if (selected) DdzColors.Gold else DdzColors.Cream, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
