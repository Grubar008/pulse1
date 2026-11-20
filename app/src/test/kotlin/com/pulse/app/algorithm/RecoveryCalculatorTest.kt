package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.algorithm.BaselineEngine
import com.pulse.app.core.algorithm.BaselineKeys
import com.pulse.app.core.algorithm.RecoveryCalculator
import com.pulse.app.core.algorithm.RecoveryInput
import com.pulse.app.core.model.BaselineEntry
import java.time.Instant
import org.junit.Test

class RecoveryCalculatorTest {

    private val engine = BaselineEngine()
    private var baselines: Map<String, BaselineEntry> = emptyMap()

    init {
        // 20 typical days to build a stable baseline
        var v = 0.0
        for (i in 1..20) {
            v += (if (i % 2 == 0) 1.0 else -1.0)
            baselines = engine.update(
                baselines,
                BaselineEngine.Update(hrvRmssd = 60.0 + v, rhr = 52.0 - v * 0.5, respRate = 14.5, spo2 = 97.0, skinTempDelta = 0.0)
            )
        }
        baselines[com.pulse.app.core.algorithm.BaselineEngineTestHelper.sleepKey()] =
            BaselineEntry("sleep_perf_proxy", 75.0, 15.0, Instant.now())
    }

    @Test
    fun `average day gives middle recovery`() {
        val result = RecoveryCalculator().calculate(
            RecoveryInput(hrvRmssd = 60.0, rhr = 52.0, respRate = 14.5, spo2 = 97.0, skinTempDelta = 0.0, sleepPerformance = 75.0),
            baselines
        )
        assertThat(result).isNotNull()
        result!!
        assertThat(result.recovery).isAtLeast(40)
        assertThat(result.recovery).isAtMost(60)
    }

    @Test
    fun `high hrv and low rhr gives high recovery`() {
        val result = RecoveryCalculator().calculate(
            RecoveryInput(hrvRmssd = 90.0, rhr = 46.0, respRate = 13.5, spo2 = 98.0, skinTempDelta = 0.0, sleepPerformance = 95.0),
            baselines
        )!!
        assertThat(result.recovery).isAtLeast(80)
    }

    @Test
    fun `low hrv and high rhr gives low recovery`() {
        val result = RecoveryCalculator().calculate(
            RecoveryInput(hrvRmssd = 35.0, rhr = 60.0, respRate = 16.5, spo2 = 95.0, skinTempDelta = 1.2, sleepPerformance = 55.0),
            baselines
        )!!
        assertThat(result.recovery).isAtMost(20)
    }

    @Test
    fun `missing inputs are renormalized not failing`() {
        val result = RecoveryCalculator().calculate(
            RecoveryInput(hrvRmssd = 90.0),
            baselines
        )!!
        assertThat(result.recovery).isAtLeast(70)
        assertThat(result.contributions.map { it.name }).containsExactly("HRV")
    }

    @Test
    fun `no inputs returns null`() {
        assertThat(RecoveryCalculator().calculate(RecoveryInput(), baselines)).isNull()
    }

    @Test
    fun `result clipped to 1..99`() {
        for (delta in -10..10) {
            val r = RecoveryCalculator().calculate(
                RecoveryInput(hrvRmssd = 200.0 + delta * 20),
                baselines
            )
            r?.let { assertThat(it.recovery).isIn(1..99) }
        }
    }
}
