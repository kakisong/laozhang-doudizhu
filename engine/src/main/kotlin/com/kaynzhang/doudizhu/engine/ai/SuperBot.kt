package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.Move
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 超级: offline DouZero inference over every move legal under this game's rules. */
class SuperBot(
    private val evaluator: DouZeroEvaluator = OnnxDouZeroEvaluator,
    private val fallback: Bot = HardBot(),
    private val analyzer: HandAnalyzer = HandAnalyzer(),
) : Bot {
    override suspend fun act(obs: Observation, ctx: BotContext): Action {
        currentCoroutineContext().ensureActive()
        val rng = Rng(ctx.seed)
        return when (obs.phase) {
            // The pretrained DouZero networks cover play, after a landlord has been selected.
            Phase.BIDDING -> Action.Bid(obs.seat, BidPolicy.bid(obs, Difficulty.HARD, analyzer, rng))
            Phase.DOUBLING -> Action.Jiabei(obs.seat, BidPolicy.jiabei(obs, Difficulty.HARD, analyzer, rng))
            Phase.PLAYING -> {
                val candidates = legalMoves(obs)
                candidates.firstOrNull { it?.counts == obs.hand.counts() }?.let { return it.toAction(obs) }
                if (candidates.size == 1) return candidates.first().toAction(obs)
                val scores = try {
                    evaluator.scores(DouZeroFeatures.encode(obs), candidates.map { it?.counts ?: Counts.EMPTY })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    return fallback.act(obs, ctx)
                } catch (_: LinkageError) {
                    // A device whose native inference runtime cannot load can still finish a game.
                    currentCoroutineContext().ensureActive()
                    return fallback.act(obs, ctx)
                }
                currentCoroutineContext().ensureActive()
                if (scores.size != candidates.size || scores.any { !it.isFinite() }) {
                    return fallback.act(obs, ctx)
                }
                var best = 0
                for (index in 1 until scores.size) if (scores[index] > scores[best]) best = index
                candidates[best].toAction(obs)
            }
            Phase.FINISHED -> error("the game is over")
        }
    }

    private fun legalMoves(obs: Observation): List<Move?> {
        val hand = obs.hand.counts()
        return if (obs.isLeading) {
            MoveGenerator.leads(hand, KickerMode.ALL)
        } else {
            MoveGenerator.beating(hand, obs.trick!!, KickerMode.ALL) + null
        }
    }
}
