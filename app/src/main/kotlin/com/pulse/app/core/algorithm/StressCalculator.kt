package com.pulse.app.core.algorithm

import com.pulse.app.core.model.BaselineEntry
import com.pulse.app.core.model.BaselineEntry as BE
import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.HrvSample
import com.pulse.app.core.model.SleepSession
import com.pulse.app.core.model.StressBand
import com.pulse.app.core.model.Workout
import java.time.Instant

data class StressWindow(val ts: Instant, val level: Int, val band: StressBand, val hrZ: Double, val hrvZ: Double?)

data class StressResult(
    val windows: List<StressWindow>,
    val bandPct: Map<StressBand, Double>,
    val avgLevel: Double
)

class StressCalculator {

    fun calculate(
        hrSamples: List<HrSample>,
        hrvSamples: List<HrvSample>,
        workouts: List<Workout>,
        sleepSessions: List<SleepSession>,
        baselines: Map<String, BE>
    ): StressResult {
        val restingBase = baselines[BaselineKeys.DAYTIME_RHR]
            ?: baselines[BaselineKeys.RHR]
            ?: return StressResult(emptyList(), defaultBands(), 0.0)
        if (restingBase.sd <= 1e-6) return StressResult(emptyList(), defaultBands(), 0.0)

        val hrvByKey = hrvSamples.groupBy { it.ts.epochSecond / (AlgorithmsConfig.STRESS_WINDOW_MIN * 60) }

        val windows = mutableListOf<StressWindow>()
        val grouped = hrSamples
            .groupBy { it.ts.epochSecond / (AlgorithmsConfig.STRESS_WINDOW_MIN * 60) }
            .toSortedMap()

        for ((bucket, samples) in grouped) {
            val ts = Instant.ofEpochSecond(bucket * AlgorithmsConfig.STRESS_WINDOW_MIN * 60)
            if (isAsleep(ts, sleepSessions)) continue
            if (isInWorkout(ts, workouts)) continue

            val avgHr = samples.map { it.bpm }.average()
            val zHr = (avgHr - restingBase.mean) / restingBase.sd
            val nearHrv = hrvByKey[minOf(bucket, hrvByKey.keys.maxOrNull() ?: bucket)]
                ?.firstOrNull() ?: hrvByKey.values.flatten().minByOrNull { kotlin.math.abs(it.ts.epochSecond - ts.epochSecond) }
            val zHrv = nearHrv?.let { hv ->
                baselines[BaselineKeys.HRV_LN]?.takeIf { it.sd > 1e-6 }?.let { b ->
                    (kotlin.math.ln(hv.rmssd) - b.mean) / b.sd
                }
            }

            var level = when {
                zHr < AlgorithmsConfig.STRESS_Z_CALM -> 0
                zHr < AlgorithmsConfig.STRESS_Z_MILD -> 1
                zHr < AlgorithmsConfig.STRESS_Z_HIGH -> 2
                else -> 3
            }
            if (zHrv != null && zHrv < -1.0 && level < 3) level = maxOf(level, 2)

            val band = when (level) {
                0 -> StressBand.CALM
                1 -> StressBand.MILD
                2 -> StressBand.MODERATE
                else -> StressBand.HIGH
            }
            windows.add(StressWindow(ts, level, band, zHr, zHrv))
        }

        val n = windows.size.toDouble()
        val bandPct = if (n > 0) StressBand.entries.associateWith { band ->
            windows.count { it.band == band } * 100.0 / n
        } else defaultBands()

        val avg = if (n > 0) windows.sumOf { it.level } / n else 0.0
        return StressResult(windows, bandPct, avg)
    }

    private fun isAsleep(ts: Instant, sessions: List<SleepSession>): Boolean =
        sessions.any { !ts.isBefore(it.start) && ts.isBefore(it.end) }

    private fun isInWorkout(ts: Instant, workouts: List<Workout>): Boolean =
        workouts.any { !ts.isBefore(it.start.minusSeconds(120)) && ts.isBefore(it.end.plusSeconds(120)) }

    private fun defaultBands(): Map<StressBand, Double> =
        StressBand.entries.associateWith { 0.0 }
}
