package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.algorithm.StrainCalculator
import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.Sex
import org.junit.Test
import java.time.Instant

class StrainCalculatorTest {

    private val calc = StrainCalculator()

    @Test
    fun `zero activity gives zero strain`() {
        assertThat(calc.strainFromTrimp(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `trimp max maps to strain 21`() {
        assertThat(calc.strainFromTrimp(600.0)).isWithin(1e-6).of(21.0)
    }

    @Test
    fun `strain is monotonic in trimp`() {
        val s1 = calc.strainFromTrimp(50.0)
        val s2 = calc.strainFromTrimp(200.0)
        val s3 = calc.strainFromTrimp(800.0)
        assertThat(s1).isLessThan(s2)
        assertThat(s2).isLessThan(s3)
        assertThat(s3).isAtMost(21.0)
    }

    @Test
    fun `hrr formula`() {
        assertThat(calc.hrr(100.0, 50.0, 200)).isWithin(1e-9).of(1.0 / 3.0)
        assertThat(calc.hrr(50.0, 50.0, 200)).isEqualTo(0.0)
        assertThat(calc.hrr(250.0, 50.0, 200)).isEqualTo(1.0)
    }

    @Test
    fun `male factor exceeds female at same hrr`() {
        val m = calc.trimpForMinute(0.7, Sex.MALE)
        val f = calc.trimpForMinute(0.7, Sex.FEMALE)
        assertThat(m).isGreaterThan(f)
    }

    @Test
    fun `daily trimp accumulates over resting day`() {
        val samples = (0 until 720).map { HrSample(Instant.ofEpochSecond(1000L + it), 60.0, Sex.MALE.stableSource()) }
        val trimp = calc.dailyTrimp(samples, 50.0, 190, Sex.MALE)
        assertThat(trimp).isGreaterThan(0.0)
    }

    @Test
    fun `zone minutes bounded`() {
        val samples = (0 until 100).map { HrSample(Instant.ofEpochSecond(it.toLong()), (if (it % 2 == 0) 60.0 else 180.0), Sex.MALE.stableSource()) }
        val zones = calc.zoneMinutes(samples, 50.0, 190)
        assertThat(zones.sum()).isEqualTo(100)
    }
}

private fun Sex.stableSource(): com.pulse.app.core.model.SourceId = com.pulse.app.core.model.SourceId.HEALTH_CONNECT
