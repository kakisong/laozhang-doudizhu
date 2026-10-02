package com.kaynzhang.doudizhu

import android.app.Application
import com.kaynzhang.doudizhu.audio.AppAudio
import com.kaynzhang.doudizhu.audio.SoundManager
import com.kaynzhang.doudizhu.audio.VoiceAnnouncer
import com.kaynzhang.doudizhu.data.AppDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-scoped singletons. Writes and audio outlive any single screen or ViewModel. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = AppDataStore(app, scope)
    val sound = SoundManager(app, scope)
    val voice = VoiceAnnouncer(app)
    val audio = AppAudio(app, sound, voice)

    init {
        scope.launch {
            store.data.collect { d ->
                audio.apply(d.settings)
            }
        }
    }
}

class DdzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
