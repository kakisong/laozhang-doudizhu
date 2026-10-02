package com.kaynzhang.doudizhu.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Generates computer opponents with a bankroll that fits the room. */
object Personas {

    private val NAMES = listOf(
        "王大爷" to "👴", "李阿姨" to "👵", "小张" to "🧑", "老刘" to "🧔", "阿花" to "👩",
        "赵老板" to "🤵", "孙师傅" to "👨‍🔧", "周同学" to "🧑‍🎓", "吴姐" to "👩‍💼", "郑哥" to "😎",
        "钱多多" to "🤑", "陈博士" to "🧑‍🔬",
    )

    /** Two distinct opponents; index 0 of the returned list is a placeholder for the human seat. */
    fun forRoom(room: Room, random: Random = Random.Default): List<Persona> {
        val picks = NAMES.shuffled(random).take(2)
        return listOf(Persona("我", "🙂", 0)) + picks.map { (name, avatar) -> Persona(name, avatar, bankroll(room, random)) }
    }

    /** Replaces opponents who can no longer afford the room, keeping the others. */
    fun refresh(personas: List<Persona>, room: Room, random: Random = Random.Default): List<Persona> {
        val taken = personas.map { it.name }.toMutableSet()
        return personas.mapIndexed { seat, p ->
            if (seat == 0 || p.coins >= room.minCoins.coerceAtLeast(room.baseScore * 10)) {
                p
            } else {
                val (name, avatar) = NAMES.filter { it.first !in taken }.random(random)
                taken += name
                Persona(name, avatar, bankroll(room, random))
            }
        }
    }

    /** Log-uniform between 50 and 500 times the base score. */
    private fun bankroll(room: Room, random: Random): Long {
        val lo = ln(50.0)
        val hi = ln(500.0)
        return (room.baseScore * exp(lo + random.nextDouble() * (hi - lo))).toLong()
    }
}
