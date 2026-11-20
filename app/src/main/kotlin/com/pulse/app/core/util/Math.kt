package com.pulse.app.core.util

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

object MathUtil {

    const val SQRT2 = 1.4142135623730951

    fun normalCdf(x: Double): Double = 0.5 * erf(-x / SQRT2)

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
        if (x > (a + 1) / (a + b + 2)) return 1.0 - incompleteBeta(b, a, 1.0 - x)

        val lbeta = lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1.0 - x)
        val front = exp(lbeta) / a

        var f = 1.0
        var c = 1.0
        var d = 0.0
        for (i in 0..199) {
            val m = i / 2
            var num: Double = if (i == 0) 1.0 else m * (b - m) * x / ((a + 2 * m - 1.0) * (a + 2 * m))
            d = 1.0 + num * d
            if (d == 0.0) d = 1e-30
            d = 1.0 / d
            c = 1.0 + num / c
            if (c == 0.0) c = 1e-30
            f *= c * d

            num = -(a + m) * (a + b + m) * x / ((a + 2 * m) * (a + 2 * m + 1.0))
            d = 1.0 + num * d
            if (d == 0.0) d = 1e-30
            d = 1.0 / d
            c = 1.0 + num / c
            if (c == 0.0) c = 1e-30
            val delta = c * d
            f *= delta
            if (abs(delta - 1.0) < 1e-10) break
        }
        return front * (f - 1.0)
    }

    fun studentTCdf(t: Double, df: Double): Double {
        if (df <= 0) return Double.NaN
        val x = df / (df + t * t)
        val p = 0.5 * incompleteBeta(df / 2.0, 0.5, x)
        return if (t > 0) 1.0 - p else p
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)
}
