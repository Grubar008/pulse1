package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.algorithm.SleepCalculator
import com.pulse.app.core.model.SleepSession
import com.pulse.app.core.model.SleepStage
import com.pulse.app.core.model.SleepStageType
import org.junit.Test
import java.time.Instant

class SleepCalculatorTest {

    private val calc = SleepCalculator()

    private fun session(mins: Long, thirds: Triple<Long, Long, Long> = Triple(100, 200, 90)): SleepSession {
        val start = Instant.parse("2025-01-10T23:00:00Z")
        val stages = buildList {
            var t = start
            listOf(SleepStageType.DEEP, SleepStageType.LIGHT, SleepStageType.REM).forEachIndexed { i, type ->
                val dur = listOf(thirds.first, thirds.second, thirds.third)[i] * mins
                val end = t.plusSeconds((dur * 60 / 390).toLong())
                add(SleepStage(type, t, end)); t = end
            }
        }
        val end = start.plusSeconds(mins * 60)
        return SleepSession(start, end, stages.dropLast(1) + SleepStage(SleepStageType.REM, stages.last().end, end), SleepStageTypeAndSource.source)
    }

    @Test
    fun `nap detection by duration`() {
        val s = SleepSession(Instant.now(), Instant.now().plusSeconds(3600), emptyList(), SleepStageTypeAndSource.source)
        assertThat(calc.isNap(s)).isTrue()
        val main = SleepSession(Instant.now(), Instant.now().plusSeconds(6 * 3600), emptyList(), SleepStageTypeAndSource.source)
        assertThat(calc.isNap(main)).isFalse()
    }

    @Test
    fun `session under 3h is a nap and never main sleep`() {
        val short = SleepSession(Instant.now(), Instant.now().plusSeconds(2 * 3600), emptyList(), SleepStageTypeAndSource.source)
        val main = SleepSession(Instant.now().minusSeconds(8 * 3600), Instant.now(), emptyList(), SleepStageTypeAndSource.source)
        val m = calc.computeMetrics(listOf(short, main))
        assertThat(m.mainSleep!!.start).isEqualTo(main.start)
        assertThat(m.naps).hasSize(1)
    }

    @Test
    fun `sleep need adds strain and debt`() {
        val base = 480.0
        val needHighStrain = calc.sleepNeed(base, 21.0, emptyList(), 0.0)
        val needRest = calc.sleepNeed(base, 0.0, emptyList(), 0.0)
        assertThat(needHighStrain.strainAddMin).isGreaterThan(needRest.strainAddMin)
        assertThat(needRest.strainAddMin).isWithin(0.001).of(0.0)
    }

    @Test
    fun `debt capped`() {
        val need = calc.sleepNeed(480.0, 0.0, List(5) { 300.0 }, 0.0)
        assertThat(need.debtAddMin).isWithin(0.001).of(60.0)
    }

    @Test
    fun `performance min 100`() {
        assertThat(calc.performance(600, 480.0)).isEqualTo(100)
        assertThat(calc.performance(240, 480.0)).isEqualTo(50)
    }

    @Test
    fun `planner bedtimes move earlier for higher targets`() {
        val plan = calc.planner(480.0, 420.0)
        assertThat(plan[100]!!).isLessThan(plan[85]!!)
        assertThat(plan[85]!!).isLessThan(plan[70]!!)
    }

    @Test
    fun `efficiency asleep over in bed`() {
        assertThat(calc.efficiency(400, 500)).isEqualTo(80)
    }
}

private object SleepStageTypeAndSource {
    val source = com.pulse.app.core.model.SourceId.HEALTH_CONNECT
}
