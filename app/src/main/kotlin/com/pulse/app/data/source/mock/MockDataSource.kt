package com.pulse.app.data.source.mock

import com.pulse.app.core.model.*
import com.pulse.app.data.source.DataSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class MockDataSource(private val seed: Long = 42L) : DataSource {

    override val id: SourceId = SourceId.HEALTH_CONNECT
    override val availableMetrics: Set<MetricType> = MetricType.entries.toSet()

    private val rnd = Random(seed)

    override suspend fun status(): SourceStatus = SourceStatus.Connected

    override suspend fun readHeartRate(range: TimeRange): List<HrSample> {
        val out = mutableListOf<HrSample>()
        var t = range.start
        while (t < range.end) {
            val zoned = t.atZone(ZoneId.systemDefault())
            val hour = zoned.hour + zoned.minute / 60.0
            val asleep = hour < 7 || hour >= 23
            val base = if (asleep) 52.0 else 68.0
            val bpm = base + 6.0 * kotlin.math.sin(hour / 24.0 * 2 * Math.PI) + rnd.nextDouble(-3.0, 3.0)
            out.add(HrSample(t, bpm.coerceIn(40.0, 200.0), SourceId.HEALTH_CONNECT))
            t = t.plusSeconds(60)
        }
        return out
    }

    override suspend fun readHrv(range: TimeRange): List<HrvSample> {
        val out = mutableListOf<HrvSample>()
        var day = LocalDate.ofInstant(range.start, ZoneId.systemDefault())
        val end = LocalDate.ofInstant(range.end, ZoneId.systemDefault())
        var i = 0
        while (day <= end) {
            val t = day.atTime(6, 30).atZone(ZoneId.systemDefault()).toInstant().plusSeconds(rnd.nextLong(-900, 900))
            if (t in range.start..range.end) {
                val rmssd = 55.0 + 5.0 * kotlin.math.sin(i / 7.0 * 2 * Math.PI) + rnd.nextDouble(-8.0, 8.0)
                out.add(HrvSample(t, rmssd.coerceIn(15.0, 140.0), SourceId.HEALTH_CONNECT))
            }
            day = day.plusDays(1); i++
        }
        return out
    }

    override suspend fun readRestingHr(range: TimeRange): List<HrSample> {
        return readHeartRate(range).filter { it.bpm < 60 }.distinctBy { it.ts }
    }

    override suspend fun readSleepSessions(range: TimeRange): List<SleepSession> {
        val out = mutableListOf<SleepSession>()
        var day = LocalDate.ofInstant(range.start, ZoneId.systemDefault())
        val end = LocalDate.ofInstant(range.end, ZoneId.systemDefault())
        var i = 0
        while (day <= end) {
            val start = day.minusDays(1).atTime(23, 20).atZone(ZoneId.systemDefault()).toInstant()
                .plusSeconds(rnd.nextLong(-1800, 1800))
            val durationMin = 430L + rnd.nextLong(-40, 25)
            val end2 = start.plus(Duration.ofMinutes(durationMin))
            out.add(SleepSession(start, end2, syntheticStages(start, end2), SourceId.HEALTH_CONNECT))
            day = day.plusDays(1); i++
        }
        return out
    }

    private fun syntheticStages(start: Instant, end: Instant): List<SleepStage> {
        val stages = mutableListOf<SleepStage>()
        var t = start
        var idx = 0
        val cycle = listOf(SleepStageType.LIGHT, SleepStageType.DEEP, SleepStageType.LIGHT, SleepStageType.REM)
        while (t < end) {
            val stage = cycle[idx % cycle.size]
            val len = 25L + rnd.nextLong(0, 20) * 60
            val s = t.plusSeconds(len)
            if (s > end) { stages.add(SleepStage(stage, t, end)); break }
            stages.add(SleepStage(stage, t, s))
            t = s; idx++
        }
        return stages
    }

    override suspend fun readSteps(range: TimeRange): List<StepsBucket> {
        val out = mutableListOf<StepsBucket>()
        var t = range.start
        while (t < range.end) {
            val zoned = t.atZone(ZoneId.systemDefault())
            val hour = zoned.hour
            val rate = if (hour in 8..21) rnd.nextDouble(0.0, 120.0) else rnd.nextDouble(0.0, 8.0)
            out.add(StepsBucket(t, t.plusSeconds(3600), rate.toLong(), SourceId.HEALTH_CONNECT))
            t = t.plusSeconds(3600)
        }
        return out
    }

    override suspend fun readExercise(range: TimeRange): List<Workout> {
        return emptyList()
    }

    override suspend fun readSpO2(range: TimeRange): List<Spo2Sample> {
        val out = mutableListOf<Spo2Sample>()
        var day = LocalDate.ofInstant(range.start, ZoneId.systemDefault())
        while (day <= LocalDate.ofInstant(range.end, ZoneId.systemDefault())) {
            val t = day.atTime(3, 0).atZone(ZoneId.systemDefault()).toInstant()
            out.add(Spo2Sample(t, 96.5 + rnd.nextDouble(-1.2, 1.2), SourceId.HEALTH_CONNECT))
            day = day.plusDays(1)
        }
        return out
    }

    override suspend fun readRespiratoryRate(range: TimeRange): List<RespSample> {
        val out = mutableListOf<RespSample>()
        var day = LocalDate.ofInstant(range.start, ZoneId.systemDefault())
        while (day <= LocalDate.ofInstant(range.end, ZoneId.systemDefault())) {
            val t = day.atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant()
            out.add(RespSample(t, 14.5 + rnd.nextDouble(-1.0, 1.0), SourceId.HEALTH_CONNECT))
            day = day.plusDays(1)
        }
        return out
    }

    override suspend fun readSkinTemp(range: TimeRange): List<SkinTempSample> {
        val out = mutableListOf<SkinTempSample>()
        var day = LocalDate.ofInstant(range.start, ZoneId.systemDefault())
        while (day <= LocalDate.ofInstant(range.end, ZoneId.systemDefault())) {
            val t = day.atTime(5, 0).atZone(ZoneId.systemDefault()).toInstant()
            out.add(SkinTempSample(t, rnd.nextDouble(-0.3, 0.3), SourceId.HEALTH_CONNECT))
            day = day.plusDays(1)
        }
        return out
    }

    override suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>> {
        return emptyList()
    }
}
