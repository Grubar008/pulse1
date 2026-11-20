package com.pulse.app.core.util

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

object MathUtil {

    const val SQRT2 = 1.4142135623730951

    fun normalCdf(x: Double): Double = 0.5 * (1.0 + erf(x / SQRT2))

    fun erf(x: Double): Double {
        if (x.isNaN()) return x
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t -
            0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }

    fun lnGamma(x: Double): Double {
        val cof = doubleArrayOf(
            76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
        )
        var y = x
        val tmp0 = x + 5.5
        val tmp = tmp0 - (x + 0.5) * ln(tmp0)
        var ser = 1.000000000190015
        for (j in cof.indices) { y += 1.0; ser += cof[j] / y }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    fun incompleteBeta(a: Double, b: Double, x: Double): Double {
        require(x in 0.0..1.0) { "x out of range: $x" }
        if (x == 0.0) return 0.0
        if (x == 1.0) return 1.0
        val lbeta = lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1.0 - x)
        val bt = exp(lbeta)
        return if (x < (a + 1.0) / (a + b + 2.0)) {
            bt * betacf(a, b, x) / a
        } else {
            1.0 - bt * betacf(b, a, 1.0 - x) / b
        }
    }

    private fun betacf(a: Double, b: Double, x: Double): Double {
        val maxIter = 300
        val eps = 3.0e-12
        val fpmin = 1.0e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < fpmin) d = fpmin
        d = 1.0 / d
        var h = d
        for (m in 1..maxIter) {
            val twoM = 2 * m
            var aa = m * (b - m) * x / ((qam + twoM) * (a + twoM))
            d = 1.0 + aa * d
            if (abs(d) < fpmin) d = fpmin
            c = 1.0 + aa / c
            if (abs(c) < fpmin) c = fpmin
            d = 1.0 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + twoM) * (qap + twoM))
            d = 1.0 + aa * d
            if (abs(d) < fpmin) d = fpmin
            c = 1.0 + aa / c
            if (abs(c) < fpmin) c = fpmin
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < eps) break
        }
        return h
    }

    fun studentTCdf(t: Double, df: Double): Double {
        if (df <= 0) return Double.NaN
        val x = df / (df + t * t)
        val p = 0.5 * incompleteBeta(df / 2.0, 0.5, x)
        return if (t > 0) 1.0 - p else p
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)
}
