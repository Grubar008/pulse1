package com.pulse.app.core.algorithm

import com.pulse.app.core.model.SleepSession
import com.pulse.app.core.model.SleepStageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class SleepMetrics(
    val mainSleep: SleepSession?,
    val naps: List<SleepSession>,
    val asleepMin: Long,
    val inBedMin: Long,
    val efficiency: Double,
    val latencyMin: Long,
    val disturbances: Int,
    val deepMin: Long,
    val remMin: Long,
    val lightMin: Long,
    val awakeMin: Long,
    val bedtime: Instant?,
    val wakeTime: Instant?
)

data class SleepNeed(
    val needMin: Double,
    val baseMin: Double,
    val strainAddMin: Double,
    val debtAddMin: Double,
    val napCreditMin: Double
)

class SleepCalculator {

    fun computeMetrics(sessions: List<SleepSession>): SleepMetrics {
        val view = sessions.sortedBy { it.start }
        val naps = view.filter { it.durationMin < AlgorithmsConfig.NAP_MAX_MIN }
        val mainCandidates = view.filter { it.durationMin >= AlgorithmsConfig.NAP_MAX_MIN }
        val main = mainCandidates.maxByOrNull { it.asleepMin }

        var asleep = 0L
        var inBed = 0L
        var awake = 0L
        var deep = 0L
        var rem = 0L
        var light = 0L
        var disturbances = 0
        var latency = 0L

        main?.let { m ->
            inBed = m.durationMin
            for (st in m.stages) {
                when (st.type) {
                    SleepStageType.AWAKE -> { awake += st.durationMin; disturbances++ }
                    SleepStageType.LIGHT -> light += st.durationMin
                    SleepStageType.DEEP -> deep += st.durationMin
                    SleepStageType.REM -> rem += st.durationMin
                }
            }
            asleep = light + deep + rem
            latency = m.stages.firstOrNull()?.let { it.type != SleepStageType.AWAKE }
                ?.let { if (it) 0L else m.stages.firstOrNull { s -> s.type != SleepStageType.AWAKE }?.durationMin ?: 0 }
                ?: 0L
        }

        val napMin = naps.sumOf { it.asleepMin }
        asleep += napMin

        return SleepMetrics(
            mainSleep = main,
            naps = naps,
            asleepMin = asleep,
            inBedMin = inBed,
            efficiency = if (inBed > 0) asleep * 100.0 / inBed else 0.0,
            latencyMin = latency,
            disturbances = disturbances,
            deepMin = deep,
            remMin = rem,
            lightMin = light,
            awakeMin = awake,
            bedtime = main?.start,
            wakeTime = main?.end
        )
    }

    fun sleepNeed(
        baseMin: Double,
        recentStrain: Double,
        recentNightsAsleepMin: List<Double>,
        napMin: Double
    ): SleepNeed {
        val avgAsleep = if (recentNightsAsleepMin.isNotEmpty()) recentNightsAsleepMin.average() else baseMin
        val personalizedBase = if (recentNightsAsleepMin.size >= 5) 0.5 * baseMin + 0.5 * avgAsleep else baseMin

        val strainAdd = AlgorithmsConfig.SLEEP_STRAIN_ADD_MAX_MIN * (recentStrain / 21.0).coerceIn(0.0, 1.0)

        var debt = 0.0
        for (asleep in recentNightsAsleepMin) {
            debt += maxOf(0.0, personalizedBase - asleep)
        }
        debt = min(debt / recentNightsAsleepMin.size.coerceAtLeast(1) * AlgorithmsConfig.SLEEP_DEBT_FACTOR, AlgorithmsConfig.SLEEP_DEBT_CAP_MIN)

        val need = personalizedBase + strainAdd + debt - napMin
        return SleepNeed(
            needMin = need.coerceAtLeast(240.0),
            baseMin = personalizedBase,
            strainAddMin = strainAdd,
            debtAddMin = debt,
            napCreditMin = napMin
        )
    }

    fun performance(asleepMin: Long, needMin: Double): Int {
        if (needMin <= 0) return 0
        return (asleepMin / needMin * 100.0).toInt().coerceIn(0, 100)
    }

    fun efficiency(asleepMin: Long, inBedMin: Long): Int {
        if (inBedMin <= 0) return 0
        return (asleepMin * 100.0 / inBedMin).toInt().coerceIn(0, 100)
    }

    fun consistency(bedtimes: List<Double>, wakeTimes: List<Double>): Int? {
        if (bedtimes.size < 2 || wakeTimes.size < 2) return null
        val sdBed = sdOfAngles(bedtimes)
        val sdWake = sdOfAngles(wakeTimes)
        val avg = (sdBed + sdWake) / 2.0
        val c = (100.0 - AlgorithmsConfig.CONSISTENCY_SCALE * avg).toInt().coerceIn(0, 100)
        return c
    }

    private fun sdOfAngles(minutes: List<Double>): Double {
        val n = minutes.size
        val sinSum = minutes.sumOf { kotlin.math.sin(it * 2.0 * Math.PI / 1440.0) }
        val cosSum = minutes.sumOf { kotlin.math.cos(it * 2.0 * Math.PI / 1440.0) }
        val r = sqrt(sinSum * sinSum + cosSum * cosSum) / n
        return (1 - r) * 720.0
    }

    fun planner(
        needMin: Double,
        usualWakeMinFromMidnight: Double,
        targets: List<Int> = listOf(70, 85, 100)
    ): Map<Int, Double> {
        val out = mutableMapOf<Int, Double>()
        for (t in targets) {
            val required = needMin / (t / 100.0)
            var bedtime = usualWakeMinFromMidnight - required
            while (bedtime < 0) bedtime += 1440.0
            out[t] = bedtime % 1440.0
        }
        return out
    }

    fun isNap(session: SleepSession): Boolean = session.durationMin < AlgorithmsConfig.NAP_MAX_MIN
}
