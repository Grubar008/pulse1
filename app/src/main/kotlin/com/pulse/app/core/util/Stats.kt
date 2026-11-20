package com.pulse.app.core.util

import kotlin.math.sqrt

data class EwStats(val decay: Double) {
    var mean: Double = 0.0
        private set
    var variance: Double = 0.0
        private set
    var count: Int = 0
        private set

    val sd: Double get() = sqrt(variance)

    fun update(x: Double) {
        require(x.isFinite())
        if (count == 0 || variance == 0.0 && count <= 1) {
            mean = x
            count += 1
            return
        }
        val old = mean
        mean += decay * (x - mean)
        variance = (1 - decay) * (variance + decay * (x - old) * (x - old))
        count += 1
    }
}

object Stats {
    fun mean(v: List<Double>): Double = if (v.isEmpty()) Double.NaN else v.sum() / v.size

    fun sd(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = mean(v)
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }
}
