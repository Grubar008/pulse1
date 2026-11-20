package com.pulse.app.core.util

import kotlin.math.sqrt

data class EwStats(val decay: Double) {
    private var m1: Double = 0.0
    private var m2: Double = 0.0
    private var count: Int = 0

    var mean: Double = 0.0
        private set
    var variance: Double = 0.0
        private set
    val sd: Double get() = sqrt(variance)

    fun update(x: Double) {
        require(x.isFinite())
        if (count == 0) {
            m1 = x
            m2 = x * x
            mean = x
            count = 1
            return
        }
        m1 += decay * (x - m1)
        m2 += decay * (x * x - m2)
        mean = m1
        variance = (m2 - m1 * m1).coerceAtLeast(0.0)
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
