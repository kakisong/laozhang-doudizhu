package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.BidStage
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.model.Rng
import kotlin.math.exp

/**
 * Logistic estimate of the landlord's chance to win from the landlord's hand.
 * Coefficients were fitted on 20k forced-landlord Normal-vs-Normal games
 * (CalibrationTest, `./gradlew :engine:slowTest --tests '*CalibrationTest*'`).
 */
object BidModel {

    /** Before the bottom cards: the 17-card hand of a prospective landlord. */
    val W17 = doubleArrayOf(0.0460, 0.3145, 0.1844, 0.1907)

    /** After the bottom cards: the landlord's 20-card hand. */
    val W20 = doubleArrayOf(1.2672, 0.7240, 0.1379, 0.3493)

    fun features(hand: Counts, analyzer: HandAnalyzer): DoubleArray {
        val plan = analyzer.plan(hand)
        return doubleArrayOf(1.0, plan.score / 10.0, bigCards(hand).toDouble(), plan.turns.toDouble())
    }

    /** 大王 3, 小王 2, each 2 one point, each bomb 4. */
    fun bigCards(hand: Counts): Int {
        var big = 3 * hand[Rk.BJ] + 2 * hand[Rk.SJ] + hand[Rk.TWO]
        for (r in 0..Rk.TWO) if (hand[r] == 4) big += 4
        return big
    }

    fun winProbability(hand: Counts, analyzer: HandAnalyzer): Double {
        val w = if (hand.total >= 20) W20 else W17
        val f = features(hand, analyzer)
        var z = 0.0
        for (i in w.indices) z += w[i] * f[i]
        return 1.0 / (1.0 + exp(-z))
    }
}

/** 叫地主/抢地主 and 加倍 decisions for all difficulties. */
object BidPolicy {

    fun bid(obs: Observation, difficulty: Difficulty, analyzer: HandAnalyzer, rng: Rng): Boolean {
        val p = BidModel.winProbability(obs.hand.counts(), analyzer) + noise(difficulty, rng)
        val threshold = when (obs.bidding.stage) {
            BidStage.CALL -> if (difficulty == Difficulty.HARD) 0.50 else 0.45
            BidStage.ROB -> 0.55
            BidStage.FINAL -> 0.60
            BidStage.DONE -> return false
        }
        return p >= threshold
    }

    fun jiabei(obs: Observation, difficulty: Difficulty, analyzer: HandAnalyzer, rng: Rng): Boolean {
        val hand = obs.hand.counts()
        if (obs.isLandlord) {
            return BidModel.winProbability(hand, analyzer) + noise(difficulty, rng) >= 0.62
        }
        // A farmer doubles with a hand that would itself have been worth calling, as long as
        // the bottom cards did not hand the landlord 2s or jokers.
        val bottom = obs.bottom?.counts() ?: Counts.EMPTY
        val bottomBig = bottom[Rk.TWO] + bottom[Rk.SJ] + bottom[Rk.BJ]
        return bottomBig == 0 && BidModel.winProbability(hand, analyzer) + noise(difficulty, rng) >= 0.60
    }

    private fun noise(difficulty: Difficulty, rng: Rng): Double =
        if (difficulty == Difficulty.EASY) (rng.nextDouble() - 0.5) * 0.3 else 0.0
}
