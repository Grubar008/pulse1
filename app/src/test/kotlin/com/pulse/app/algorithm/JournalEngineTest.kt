package com.pulse.app.algorithm

import com.google.common.truth.Truth.assertThat
import com.pulse.app.core.algorithm.JournalEngine
import com.pulse.app.core.model.JournalEntry
import com.pulse.app.core.model.Scores
import org.junit.Test
import java.time.LocalDate

class JournalEngineTest {

    private val engine = JournalEngine()
    private val start = LocalDate.parse("2025-01-01")

    private fun scoresWith(recovery: (LocalDate) -> Int): Map<LocalDate, Scores> =
        (0..60L).associate { i ->
            val d = start.plusDays(i)
            d to Scores(date = d, recovery = recovery(d))
        }

    @Test
    fun `alcohol negative effect detected`() {
        val entries = mutableListOf<JournalEntry>()
        val rec = fun(d: LocalDate): Int = if (entries.any { it.date.plusDays(1) == d && it.value > 0 }) 55 else 75
        entries.run {
            (0..9L).forEach { i -> add(JournalEntry(start.plusDays(i), "alcohol", 1.0)) }
            (15..34L).forEach { i -> add(JournalEntry(start.plusDays(i), "alcohol", 0.0)) }
        }
        val scores = (0..40L).associate { i -> start.plusDays(i) to Scores(start.plusDays(i), recovery = rec(start.plusDays(i))) }
        val effects = engine.analyze(entries, scores)
        assertThat(effects).isNotEmpty()
        val alcohol = effects.first { it.behaviorId == "alcohol" }
        assertThat(alcohol.deltaRecovery).isLessThan(0.0)
        assertThat(alcohol.confidence).isIn(listOf("strong", "likely"))
    }

    @Test
    fun `insufficient samples yield no insight`() {
        val entries = (0..3L).map { JournalEntry(start.plusDays(it), "caffeine", 1.0) }
        val scores = scoresWith { 75 }
        assertThat(engine.analyze(entries, scores)).isEmpty()
    }

    @Test
    fun `welch t-test on identical groups gives p 1`() {
        val p = engine.welch(70.0, 10.0, 20, 70.0, 10.0, 20)
        assertThat(p).isAtLeast(0.9)
    }
}
