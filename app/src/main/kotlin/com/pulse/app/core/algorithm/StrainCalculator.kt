package com.pulse.app.core.algorithm

import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.Sex
import com.pulse.app.core.model.Workout
import kotlin.math.exp
import kotlin.math.ln

class StrainCalculator {

    fun hrMax(age: Int, sex: Sex = Sex.OTHER): Int = 208 - (70 * age) / 100

    fun hrr(hr: Double, rhr: Double, hrMax: Int): Double {
        if (hrMax <= rhr + 1) return 0.0
        return ((hr - rhr) / (hrMax - rhr)).coerceIn(0.0, 1.0)
    }

    fun trimpForMinute(hrrValue: Double, sex: Sex): Double {
        val factor = when (sex) {
            Sex.MALE -> AlgorithmsConfig.BANISTER_MALE_A * exp(AlgorithmsConfig.BANISTER_MALE_B * hrrValue)
            else -> AlgorithmsConfig.BANISTER_FEMALE_A * exp(AlgorithmsConfig.BANISTER_FEMALE_B * hrrValue)
        }
        return hrrValue * factor
    }

    fun dailyTrimp(
        hrSamples: List<HrSample>,
        rhr: Double,
        hrMax: Int,
        sex: Sex,
        workouts: List<Workout> = emptyList()
    ): Double {
        if (hrSamples.isEmpty() || rhr <= 0) return 0.0
        val inWorkout = workouts.map { it.start.epochSecond..it.end.epochSecond }
        var total = 0.0
        val sorted = hrSamples.sortedBy { it.ts }
        var i = 0
        while (i < sorted.size) {
            val bucket = sorted.slice(i until minOf(i + 60, sorted.size))
            val avg = bucket.map { it.bpm }.average()
            val ts = bucket.first().ts
            val inWk = inWorkout.any { ts.epochSecond in it }
            if (!inWk) {
                total += trimpForMinute(hrr(avg, rhr, hrMax), sex)
            }
            i += 60
        }
        return total
    }

    fun strainFromTrimp(trimp: Double): Double {
        if (trimp <= 0) return 0.0
        val c = AlgorithmsConfig.TRIMP_C
        val tMax = AlgorithmsConfig.TRIMP_MAX
        val strain = 21.0 * ln(1 + trimp / c) / ln(1 + tMax / c)
        return strain.coerceAtMost(21.0)
    }

    fun dailyStrain(
        hrSamples: List<HrSample>,
        rhr: Double,
        hrMax: Int,
        sex: Sex,
        workouts: List<Workout> = emptyList()
    ): Pair<Double, Double> {
        val trimp = dailyTrimp(hrSamples, rhr, hrMax, sex, workouts)
        return strainFromTrimp(trimp) to trimp
    }

    fun muscularLoad(
        strengthWorkouts: List<Workout>,
        setsPerSession: Map<Int, Int> = emptyMap(),
        bodyWeightKg: Double = 75.0
    ): Double {
        return strengthWorkouts.sumOf { w ->
            val sets = setsPerSession[w.start.epochSecond.toInt()] ?: (w.durationMin / 5).toInt().coerceAtLeast(3)
            val durationLoad = w.durationMin * 2.0
            val setLoad = sets * 10.0
            durationLoad + setLoad + bodyWeightKg * 0.05
        }
    }

    fun optimalTarget(recoveryZone: Int): Double = when (recoveryZone) {
        0 -> AlgorithmsConfig.TARGET_STRAIN_RED
        1 -> AlgorithmsConfig.TARGET_STRAIN_YELLOW
        else -> AlgorithmsConfig.TARGET_STRAIN_GREEN
    }

    fun zoneMinutes(hrSamples: List<HrSample>, rhr: Double, hrMax: Int): IntArray {
        val zones = IntArray(5)
        if (rhr <= 0 || hrMax <= rhr + 1) return zones
        val span = (hrMax - rhr).toDouble()
        for ((_, bpm, _) in hrSamples) {
            val frac = ((bpm - rhr) / span).coerceIn(0.001, 0.999)
            val zone = (frac * 5).toInt().coerceIn(0, 4)
            zones[zone]++
        }
        return zones
    }
}
