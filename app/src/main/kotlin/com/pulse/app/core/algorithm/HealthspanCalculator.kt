package com.pulse.app.core.algorithm

import com.pulse.app.core.model.BaselineEntry
import com.pulse.app.core.model.Sex
import com.pulse.app.core.model.Scores
import kotlin.math.min

data class HealthspanInput(
    val sleepHours: Double?,
    val sleepConsistency: Int?,
    val hrvRmssd: Double?,
    val rhr: Double?,
    val vo2max: Double?,
    val steps: Double?,
    val zone1to5Minutes: IntArray,
    val strengthSessionsPerWeek: Double,
    val sex: Sex,
    val baselines: Map<String, BaselineEntry>
)

class HealthspanCalculator {

    fun paceOfAging(input: HealthspanInput): Double {
        var total = 0.0
        var weight = 0.0

        input.sleepHours?.let {
            total += 0.15 * component(it / AlgorithmsConfig.HEALTHSPAN_SLEEP_TARGET_H)
            weight += 0.15
        }
        input.sleepConsistency?.let {
            total += 0.10 * component(it / 100.0)
            weight += 0.10
        }
        input.hrvRmssd?.let { hrv ->
            input.baselines["hrv_ln"]?.takeIf { it.sd > 1e-6 }?.let { b ->
                val z = (kotlin.math.ln(hrv) - b.mean) / b.sd
                total += 0.20 * component(1.0 + 0.15 * z)
                weight += 0.20
            }
        }
        input.rhr?.let { rhr ->
            input.baselines["rhr"]?.takeIf { it.sd > 1e-6 }?.let { b ->
                val z = (b.mean - rhr) / b.sd
                total += 0.15 * component(1.0 + 0.15 * z)
                weight += 0.15
            }
        }
        input.vo2max?.let { vo2 ->
            val target = if (input.sex == Sex.FEMALE) AlgorithmsConfig.HEALTHSPAN_VO2_TARGET_FEMALE
            else AlgorithmsConfig.HEALTHSPAN_VO2_TARGET_MALE
            total += 0.20 * component(vo2 / target)
            weight += 0.20
        }
        input.steps?.let {
            total += 0.10 * component(it / AlgorithmsConfig.HEALTHSPAN_STEPS_TARGET)
            weight += 0.10
        }
        val zoneMin = input.zone1to5Minutes.sum().toDouble()
        total += 0.05 * component(zoneMin / (AlgorithmsConfig.HEALTHSPAN_HR_ZONE_TARGET_MIN * 7))
        weight += 0.05

        val strength = component(input.strengthSessionsPerWeek / AlgorithmsConfig.HEALTHSPAN_STRENGTH_PER_WEEK)
        total += 0.10 * strength
        weight += 0.10

        if (weight == 0.0) return 50.0
        return (100.0 * total / weight).coerceIn(0.0, 100.0)
    }

    private fun component(ratio: Double): Double = min(ratio, 1.5).coerceIn(0.0, 1.5) / 1.5

    fun zoneWeight(days: Int): IntArray = IntArray(5)

    companion object {
        fun fromScores(scores: Scores, input: HealthspanInput): Double =
            HealthspanCalculator().paceOfAging(input)
    }
}
