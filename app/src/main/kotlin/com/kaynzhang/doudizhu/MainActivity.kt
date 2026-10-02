package com.kaynzhang.doudizhu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.ui.screens.LobbyScreen
import com.kaynzhang.doudizhu.ui.screens.RulesScreen
import com.kaynzhang.doudizhu.ui.screens.SettingsScreen
import com.kaynzhang.doudizhu.ui.screens.StatsScreen
import com.kaynzhang.doudizhu.ui.table.TableScreen
import com.kaynzhang.doudizhu.ui.theme.DdzTheme

enum class Screen { LOBBY, TABLE, SETTINGS, STATS, RULES }

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val launch = DebugHooks.parse(intent)
        setContent {
            DdzTheme {
                Box(Modifier.fillMaxSize().then(DebugHooks.rootModifier)) {
                    App(vm, launch)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun App(vm: GameViewModel, launch: DebugLaunch?) {
    var screen by rememberSaveable { mutableStateOf(Screen.LOBBY) }
    val appData by vm.appData.collectAsStateWithLifecycle()

    // After process death the ViewModel is new: resume the saved table or fall back to the lobby.
    LaunchedEffect(screen, appData != null) {
        if (screen == Screen.TABLE && !vm.inGame && appData != null) {
            if (!vm.resumeSaved()) screen = Screen.LOBBY
        }
    }
    LaunchedEffect(appData != null) {
        if (appData != null && launch != null && DebugHooks.start(vm, launch)) screen = Screen.TABLE
    }

    when (screen) {
        Screen.LOBBY -> LobbyScreen(
            vm,
            onEnterRoom = { if (vm.enterRoom(it)) screen = Screen.TABLE },
            onResume = { if (vm.resumeSaved()) screen = Screen.TABLE },
            onSettings = { screen = Screen.SETTINGS },
            onStats = { screen = Screen.STATS },
            onRules = { screen = Screen.RULES },
        )
        Screen.TABLE -> TableScreen(vm, onLeave = {
            vm.leaveTable()
            screen = Screen.LOBBY
        })
        Screen.SETTINGS -> {
            BackHandler { screen = Screen.LOBBY }
            SettingsScreen(vm, onBack = { screen = Screen.LOBBY })
        }
        Screen.STATS -> {
            BackHandler { screen = Screen.LOBBY }
            StatsScreen(vm, onBack = { screen = Screen.LOBBY })
        }
        Screen.RULES -> {
            BackHandler { screen = Screen.LOBBY }
            RulesScreen(onBack = { screen = Screen.LOBBY })
        }
    }
}
