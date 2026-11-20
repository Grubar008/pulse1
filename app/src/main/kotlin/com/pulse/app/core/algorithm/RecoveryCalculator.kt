package com.pulse.app.core.algorithm

import com.pulse.app.core.model.BaselineEntry
import com.pulse.app.core.util.MathUtil
import kotlin.math.abs

data class RecoveryInput(
    val hrvRmssd: Double? = null,
    val rhr: Double? = null,
    val respRate: Double? = null,
    val spo2: Double? = null,
    val skinTempDelta: Double? = null,
    val sleepPerformance: Double? = null
)

data class RecoveryResult(
    val recovery: Int,
    val composite: Double,
    val contributions: List<Contribution>
) {
    data class Contribution(val name: String, val z: Double, val weight: Double, val contribution: Double)
}

class RecoveryCalculator {

    fun calculate(input: RecoveryInput, baselines: Map<String, BaselineEntry>): RecoveryResult? {
        val zHrv = z(input.hrvRmssd?.takeIf { it > 0 }?.let { lnOf(it) }, baselines, BaselineKeys.HRV_LN)
        val zRhr = z(input.rhr, baselines, BaselineKeys.RHR, invert = true)
        val zResp = z(input.respRate, baselines, BaselineKeys.RESP, invert = true)
        val zSpo2 = z(input.spo2, baselines, BaselineKeys.SPO2)
        val zSkin = abs(z(input.skinTempDelta, baselines, BaselineKeys.SKIN_TEMP))
        val zSleep = input.sleepPerformance?.let { (it - 75.0) / 15.0 }

        val terms = listOfNotNull(
            zHrv?.let { Triple("HRV", it, AlgorithmsConfig.W_HRV) },
            zRhr?.let { Triple("Resting HR", it, AlgorithmsConfig.W_RHR) },
            zResp?.let { Triple("Respiratory rate", it, AlgorithmsConfig.W_RESP) },
            zSpo2?.let { Triple("SpO2", it, AlgorithmsConfig.W_SPO2) },
            zSkin.takeIf { input.skinTempDelta != null }?.let { Triple("Skin temp", it, -AlgorithmsConfig.W_SKIN_TEMP) },
            zSleep?.let { Triple("Sleep", it, AlgorithmsConfig.W_SLEEP_PERF) }
        )

        if (terms.isEmpty()) return null

        val weightSum = terms.sumOf { it.third }
        val contributions = terms.map { (name, zv, w) ->
            RecoveryResult.Contribution(name, zv, w / weightSum, (w / weightSum) * zv)
        }
        val composite = contributions.sumOf { it.contribution }
        val recovery = (100.0 * MathUtil.normalCdf(AlgorithmsConfig.RECOVERY_K * composite))
            .coerceIn(1.0, 99.0)
            .toInt()
        return RecoveryResult(recovery, composite, contributions)
    }

    private fun z(
        value: Double?,
        baselines: Map<String, BaselineEntry>,
        key: String,
        invert: Boolean = false
    ): Double? {
        if (value == null || !value.isFinite()) return null
        val b = baselines[key] ?: return null
        val sd = b.sd.takeIf { it > 1e-6 } ?: return null
        return if (invert) (b.mean - value) / sd else (value - b.mean) / sd
    }

    private fun lnOf(rmssd: Double): Double = kotlin.math.ln(rmssd)
}
