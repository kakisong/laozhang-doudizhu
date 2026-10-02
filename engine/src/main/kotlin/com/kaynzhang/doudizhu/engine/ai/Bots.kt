package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.Move
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator

/** Turns a chosen move (null = pass) into an action with concrete cards from the hand. */
fun Move?.toAction(obs: Observation): Action =
    if (this == null) Action.Pass(obs.seat) else Action.Play(obs.seat, obs.hand.pick(counts), combo)

/** 普通: the heuristic [NormalPolicy]. */
class NormalBot(private val analyzer: HandAnalyzer = HandAnalyzer()) : Bot {
    private val policy = NormalPolicy(analyzer)

    override suspend fun act(obs: Observation, ctx: BotContext): Action {
        val rng = Rng(ctx.seed)
        return when (obs.phase) {
            Phase.BIDDING -> Action.Bid(obs.seat, BidPolicy.bid(obs, Difficulty.NORMAL, analyzer, rng))
            Phase.DOUBLING -> Action.Jiabei(obs.seat, BidPolicy.jiabei(obs, Difficulty.NORMAL, analyzer, rng))
            Phase.PLAYING -> policy.choose(obs).toAction(obs)
            Phase.FINISHED -> error("the game is over")
        }
    }
}

/**
 * 简单: plays the smallest card that beats, leads its smallest single or pair, passes at random
 * when not in danger, only bombs when an opponent is about to go out, and ignores its teammate.
 */
class EasyBot(private val analyzer: HandAnalyzer = HandAnalyzer()) : Bot {

    override suspend fun act(obs: Observation, ctx: BotContext): Action {
        val rng = Rng(ctx.seed)
        return when (obs.phase) {
            Phase.BIDDING -> Action.Bid(obs.seat, BidPolicy.bid(obs, Difficulty.EASY, analyzer, rng))
            Phase.DOUBLING -> Action.Jiabei(obs.seat, BidPolicy.jiabei(obs, Difficulty.EASY, analyzer, rng))
            Phase.PLAYING -> choose(obs, rng).toAction(obs)
            Phase.FINISHED -> error("the game is over")
        }
    }

    fun choose(obs: Observation, rng: Rng): Move? {
        val hand = obs.hand.counts()
        ComboClassifier.declareLead(hand)?.takeIf { obs.isLeading }?.let { return Move(hand, it) }
        val v = TableView(obs)
        if (obs.isLeading) {
            val r = hand.lowestRank()
            return if (hand[r] >= 2 && r < com.kaynzhang.doudizhu.engine.model.Rk.SJ) {
                Move(Counts.of(r, r), Combo(ComboType.PAIR, r))
            } else {
                Move(Counts.of(r), Combo(ComboType.SINGLE, r))
            }
        }
        val moves = MoveGenerator.beating(hand, obs.trick!!, KickerMode.CHEAPEST)
        moves.firstOrNull { it.counts == hand }?.let { return it }
        val danger = v.opponentMin <= 2
        if (!danger && rng.nextBoolean(0.2)) return null
        return moves.firstOrNull { !it.combo.isBomb } ?: if (danger) moves.firstOrNull() else null
    }
}
