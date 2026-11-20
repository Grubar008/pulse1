package com.pulse.app.core.algorithm

import com.pulse.app.core.model.JournalEffect
import com.pulse.app.core.model.JournalEntry
import com.pulse.app.core.model.Scores
import com.pulse.app.core.util.MathUtil
import com.pulse.app.core.util.Stats
import java.time.LocalDate

class JournalEngine {

    fun analyze(
        entries: List<JournalEntry>,
        scores: Map<LocalDate, Scores>,
        minValueWith: Int = 5,
        minValueWithout: Int = 5
    ): List<JournalEffect> {
        val recoveryByDate = scores.mapValues { it.value.recovery?.toDouble() ?: Double.NaN }
        val byBehavior = entries.groupBy { it.behaviorId }

        val effects = mutableListOf<JournalEffect>()
        for ((behaviorId, list) in byBehavior) {
            val withRec = mutableListOf<Double>()
            val withoutRec = mutableListOf<Double>()

            for (e in list) {
                val next = e.date.plusDays(1)
                val rec = recoveryByDate[next] ?: continue
                if (rec.isNaN()) continue
                if (isActive(e.value)) withRec.add(rec) else withoutRec.add(rec)
            }

            if (withRec.size < minValueWith || withoutRec.size < minValueWithout) continue

            val m1 = Stats.mean(withRec)
            val m0 = Stats.mean(withoutRec)
            val s1 = Stats.sd(withRec)
            val s0 = Stats.sd(withoutRec)

            val delta = m1 - m0
            val result = welch(m1, s1, withRec.size, m0, s0, withoutRec.size)
            val pooled = if (s1 == 0.0 && s0 == 0.0) 0.0 else {
                val n1 = withRec.size.toDouble(); val n0 = withoutRec.size.toDouble()
                kotlin.math.sqrt(((n1 - 1) * s1 * s1 + (n0 - 1) * s0 * s0) / (n1 + n0 - 2))
            }
            val d = if (pooled == 0.0) 0.0 else delta / pooled

            effects.add(
                JournalEffect(
                    behaviorId = behaviorId,
                    samplesWith = withRec.size,
                    samplesWithout = withoutRec.size,
                    deltaRecovery = delta,
                    effectSize = d,
                    pValue = result,
                    confidence = confidence(result, d)
                )
            )
        }
        return effects.sortedByDescending { kotlin.math.abs(it.effectSize) }
    }

    private fun isActive(value: Double): Boolean = when {
        value > 1.0 -> true
        value > 0.0 -> true
        else -> false
    }

    fun welch(m1: Double, s1: Double, n1: Int, m0: Double, s0: Double, n0: Int): Double {
        if (n1 < 2 || n0 < 2) return 1.0
        val v1 = s1 * s1 / n1
        val v0 = s0 * s0 / n0
        val denom = kotlin.math.sqrt(v1 + v0)
        if (denom == 0.0) return 1.0
        val t = (m1 - m0) / denom
        val num = (v1 + v0) * (v1 + v0)
        val df = num / (v1 * v1 / (n1 - 1) + v0 * v0 / (n0 - 1))
        return MathUtil.studentTCdf(-kotlin.math.abs(t), df) * 2.0
    }

    private fun confidence(p: Double, d: Double): String = when {
        p < 0.01 && kotlin.math.abs(d) >= 0.5 -> "strong"
        p < 0.05 -> "likely"
        p < 0.15 -> "emerging"
        else -> "inconclusive"
    }
}
