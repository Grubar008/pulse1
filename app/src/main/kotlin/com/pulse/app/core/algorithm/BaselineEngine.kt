package com.pulse.app.core.algorithm

import com.pulse.app.core.model.BaselineEntry
import com.pulse.app.core.util.EwStats
import java.time.Instant
import kotlin.math.ln

class BaselineEngine(private val decay: Double = AlgorithmsConfig.BASELINE_DECAY) {

    data class Update(
        val hrvRmssd: Double? = null,
        val rhr: Double? = null,
        val respRate: Double? = null,
        val spo2: Double? = null,
        val skinTempDelta: Double? = null,
        val daytimeRhr: Double? = null
    )

    fun update(
        existing: Map<String, BaselineEntry>,
        upd: Update,
        now: Instant = Instant.now()
    ): Map<String, BaselineEntry> {
        val out = existing.toMutableMap()

        upd.hrvRmssd?.takeIf { it > 0 }?.let {
            out.merge(BaselineKeys.HRV_LN, ln(it), now)
        }
        upd.rhr?.takeIf { it in 25.0..150.0 }?.let {
            out.merge(BaselineKeys.RHR, it, now)
        }
        upd.respRate?.takeIf { it in 5.0..40.0 }?.let {
            out.merge(BaselineKeys.RESP, it, now)
        }
        upd.spo2?.takeIf { it in 70.0..100.0 }?.let {
            out.merge(BaselineKeys.SPO2, it, now)
        }
        upd.skinTempDelta?.takeIf { it.isFinite() }?.let {
            out.merge(BaselineKeys.SKIN_TEMP, it, now)
        }
        upd.daytimeRhr?.takeIf { it in 25.0..200.0 }?.let {
            out.merge(BaselineKeys.DAYTIME_RHR, it, now)
        }
        return out
    }

    private fun MutableMap<String, BaselineEntry>.merge(key: String, value: Double, now: Instant) {
        val current = this[key]
        val stats = EwStats(decay)
        if (current != null && current.sd > 0) {
            stats.update(current.mean)
            stats.update(current.mean + current.sd)
            stats.update(current.mean - current.sd)
        }
        stats.update(value)
        this[key] = BaselineEntry(key, stats.mean, stats.sd.coerceAtLeast(minSd(key)), now)
    }

    private fun minSd(key: String): Double = when (key) {
        BaselineKeys.HRV_LN -> 0.02
        BaselineKeys.RHR -> 0.5
        BaselineKeys.RESP -> 0.1
        BaselineKeys.SPO2 -> 0.2
        BaselineKeys.SKIN_TEMP -> 0.02
        BaselineKeys.DAYTIME_RHR -> 0.5
        else -> 0.01
    }
}

object BaselineKeys {
    const val HRV_LN = "hrv_ln"
    const val RHR = "rhr"
    const val RESP = "resp"
    const val SPO2 = "spo2"
    const val SKIN_TEMP = "skin_temp"
    const val DAYTIME_RHR = "daytime_rhr"
}
