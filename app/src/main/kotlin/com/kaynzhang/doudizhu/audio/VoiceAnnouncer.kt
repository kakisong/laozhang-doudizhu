package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import java.util.Locale

/**
 * Announces plays (“对K”, “飞机”, “王炸”) with the system text-to-speech engine.
 * Stays silent when no Chinese voice is installed. App-scoped: binding TTS is slow.
 */
class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    @Volatile
    var enabled = true

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        ready = result >= TextToSpeech.LANG_AVAILABLE
        if (!ready) Log.i("VoiceAnnouncer", "no Chinese TTS voice available ($result); staying silent")
    }

    /** Speaks [text] in [seat]'s voice; [interrupt] cuts off whatever is being said. */
    fun say(text: String, seat: Int, interrupt: Boolean = true) {
        if (!enabled || !ready) return
        tts.setPitch(PITCH[seat.coerceIn(0, 2)])
        tts.setSpeechRate(1.15f)
        tts.speak(text, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "ddz")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    companion object {
        private val PITCH = floatArrayOf(1.0f, 0.8f, 1.25f)
        private val PASS = listOf("不要", "要不起", "过")

        /** The spoken line for an event, or null if the event is silent. */
        fun phrase(event: GameEvent, seq: Int): String? = when (event) {
            is GameEvent.BidMade -> event.kind.zh
            is GameEvent.Played -> combo(event.combo)
            is GameEvent.Passed -> PASS[(seq + event.seat) % PASS.size]
            is GameEvent.Alert -> if (event.cardsLeft == 1) "我只剩一张牌啦" else "我只剩两张牌啦"
            else -> null
        }

        fun combo(c: Combo): String = when (c.type) {
            ComboType.SINGLE -> rank(c.rank)
            ComboType.PAIR -> "对" + rank(c.rank)
            ComboType.TRIPLE -> "三个" + rank(c.rank)
            ComboType.TRIPLE_SINGLE -> "三带一"
            ComboType.TRIPLE_PAIR -> "三带一对"
            ComboType.STRAIGHT -> "顺子"
            ComboType.PAIR_STRAIGHT -> "连对"
            ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> "飞机"
            ComboType.FOUR_TWO_SINGLES -> "四带二"
            ComboType.FOUR_TWO_PAIRS -> "四带两对"
            ComboType.BOMB -> "炸弹"
            ComboType.ROCKET -> "王炸"
        }

        private fun rank(r: Int): String = when (r) {
            Rk.JACK -> "勾"
            Rk.QUEEN -> "圈"
            Rk.KING -> "K"
            Rk.ACE -> "尖"
            Rk.TWO -> "二"
            Rk.SJ -> "小王"
            Rk.BJ -> "大王"
            else -> "三四五六七八九十"[r].toString()
        }
    }
}
