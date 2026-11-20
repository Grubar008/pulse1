package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.util.MathUtil
import org.junit.Test

class MathUtilTest {

    @Test
    fun `normal cdf at zero is one half`() {
        assertThat(MathUtil.normalCdf(0.0)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `normal cdf asymmetric sanity`() {
        assertThat(MathUtil.normalCdf(1.96)).isWithin(0.001).of(0.975)
        assertThat(MathUtil.normalCdf(-1.96)).isWithin(0.001).of(0.025)
    }

    @Test
    fun `erf bounds and symmetry`() {
        assertThat(MathUtil.erf(0.0)).isWithin(1e-9).of(0.0)
        assertThat(MathUtil.erf(2.0)).isWithin(1e-9).of(-MathUtil.erf(-2.0))
        assertThat(MathUtil.erf(5.0)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `incomplete beta is symmetric for equal a b`() {
        val x = 0.3
        assertThat(MathUtil.incompleteBeta(2.0, 2.0, x))
            .isWithin(1e-6).of(3 * x * x - 2 * x * x * x)
    }

    @Test
    fun `student t critical values`() {
        assertEquals(0.975, MathUtil.studentTCdf(2.0, 30.0), 0.01)
        assertEquals(0.5, MathUtil.studentTCdf(0.0, 10.0), 1e-9)
    }

    private fun assertEquals(expected: Double, actual: Double, delta: Double) {
        assertThat(actual).isWithin(delta).of(expected)
    }
}
