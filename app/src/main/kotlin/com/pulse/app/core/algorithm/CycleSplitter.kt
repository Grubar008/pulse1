package com.pulse.app.core.algorithm

import com.pulse.app.core.model.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object CycleSplitter {

    fun wakeTimes(sessions: List<SleepSession>, zone: ZoneId, from: LocalDate, until: LocalDate): Map<LocalDate, Instant> {
        val map = mutableMapOf<LocalDate, Instant>()
        for (s in sessions) {
            if (s.start.atZone(zone).let { it.toLocalDate() } < from.minusDays(1)) continue
            val wake = s.end
            map[wake.atZone(zone).toLocalDate()] = wake
        }
        return map
    }

    fun cycleRange(day: LocalDate, wakeTimes: Map<LocalDate, Instant>, zone: ZoneId, fallbackWakeHour: Int = 7): Pair<Instant, Instant> {
        val wake = wakeTimes[day] ?: day.atTime(fallbackWakeHour, 0).atZone(zone).toInstant()
        val nextWake = wakeTimes[day.plusDays(1)]
            ?: day.plusDays(1).atTime(fallbackWakeHour, 0).atZone(zone).toInstant()
        return wake to nextWake
    }
}
