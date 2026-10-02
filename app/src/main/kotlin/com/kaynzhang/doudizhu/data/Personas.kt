package com.kaynzhang.doudizhu.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** Old saves identify opponents by name and emoji; new saves store the catalog's stable ID. */
val Persona.portraitId: String
    get() = BuiltInPortraits.resolve(
        if (BuiltInPortraits.all.any { it.id == avatar }) avatar else name,
    ).id

/** Generates computer opponents with a bankroll that fits the room. */
object Personas {

    /** Two distinct opponents; index 0 of the returned list is a placeholder for the human seat. */
    fun forRoom(
        room: Room,
        random: Random = Random.Default,
        humanAvatarId: String = BuiltInPortraits.DEFAULT_ID,
    ): List<Persona> {
        val humanId = BuiltInPortraits.resolve(humanAvatarId).id
        val picks = BuiltInPortraits.all.filter { it.id != humanId }.shuffled(random).take(2)
        return listOf(Persona("我", humanId, 0)) + picks.map {
            Persona(it.name, it.id, bankroll(room, random))
        }
    }

    /** Migrates old identities and removes avatar collisions without changing an active bankroll. */
    fun normalize(
        personas: List<Persona>,
        humanAvatarId: String = BuiltInPortraits.DEFAULT_ID,
        random: Random = Random.Default,
    ): List<Persona> {
        val humanId = BuiltInPortraits.resolve(humanAvatarId).id
        val normalized = personas.mapIndexed { seat, p ->
            p.copy(avatar = if (seat == 0) humanId else p.portraitId)
        }
        val taken = mutableSetOf(humanId)
        val keep = normalized.mapIndexed { seat, p -> seat == 0 || taken.add(p.avatar) }
        // Reserve every surviving identity before replacing either seat.
        val unavailable = (normalized.map { it.avatar } + taken).toMutableSet()
        return normalized.mapIndexed { seat, p ->
            if (keep[seat]) p else {
                val portrait = BuiltInPortraits.all.filter { it.id !in unavailable }.random(random)
                unavailable += portrait.id
                p.copy(name = portrait.name, avatar = portrait.id)
            }
        }
    }

    /** Replaces opponents who can no longer afford the room, keeping the others. */
    fun refresh(
        personas: List<Persona>,
        room: Room,
        random: Random = Random.Default,
        humanAvatarId: String = BuiltInPortraits.DEFAULT_ID,
    ): List<Persona> {
        val normalized = normalize(personas, humanAvatarId, random)
        val unavailable = normalized.map { it.avatar }.toMutableSet()
        val threshold = room.minCoins.coerceAtLeast(room.baseScore * 10)
        return normalized.mapIndexed { seat, p ->
            if (seat == 0 || p.coins >= threshold) p else {
                val portrait = BuiltInPortraits.all.filter { it.id !in unavailable }.random(random)
                unavailable += portrait.id
                Persona(portrait.name, portrait.id, bankroll(room, random))
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
