package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.algorithm.BaselineEngine
import com.pulse.app.core.algorithm.BaselineKeys
import java.time.Instant
import org.junit.Test

class BaselineEngineTest {

    private val now = Instant.parse("2025-01-15T10:00:00Z")

    @Test
    fun `first sample seeds mean and zero sd is clamped to minimum`() {
        val out = BaselineEngine().update(emptyMap(), BaselineEngine.Update(rhr = 55.0), now)
        val b = out[BaselineKeys.RHR]!!
        assertThat(b.mean).isEqualTo(55.0)
        assertThat(b.sd).isAtLeast(0.5)
    }

    @Test
    fun `recent samples pull mean toward new value`() {
        val e = BaselineEngine()
        var baselines = emptyMap<String, com.pulse.app.core.model.BaselineEntry>()
        var value = 55.0
        repeat(10) {
            value += 1.0
            baselines = e.update(baselines, BaselineEngine.Update(rhr = value), now)
        }
        val b = baselines[BaselineKeys.RHR]!!
        assertThat(b.mean).isGreaterThan(55.0)
        assertThat(b.mean).isLessThan(65.0)
    }

    @Test
    fun `invalid inputs are ignored`() {
        val out = BaselineEngine().update(emptyMap(), BaselineEngine.Update(rhr = 5.0, spo2 = 40.0, hrvRmssd = -3.0), now)
        assertThat(out).isEmpty()
    }

    @Test
    fun `hrv uses natural log of rmssd`() {
        val out = BaselineEngine().update(emptyMap(), BaselineEngine.Update(hrvRmssd = Math.E * 4), now)
        assertThat(out[BaselineKeys.HRV_LN]!!.mean).isWithin(1e-6).of(4.0)
    }
}
