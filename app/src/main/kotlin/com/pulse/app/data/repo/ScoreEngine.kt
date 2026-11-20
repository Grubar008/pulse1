package com.pulse.app.data.repo

import com.pulse.app.core.algorithm.*
import com.pulse.app.core.model.*
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.db.entity.ComputedScoresEntity
import com.pulse.app.data.db.entity.DailyCycleEntity
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreEngine @Inject constructor(private val db: PulseDatabase) {

    private val recoveryCalc = RecoveryCalculator()
    private val strainCalc = StrainCalculator()
    private val sleepCalc = SleepCalculator()
    private val stressCalc = StressCalculator()
    private val healthCalc = HealthspanCalculator()
    private val json = Json { ignoreUnknownKeys = true }

    fun hrMaxFor(profile: UserProfileEntity): Int =
        profile.hrMax.takeIf { it > 100 } ?: (208 - (70 * profile.age) / 100)

    suspend fun computeDay(date: LocalDate, zone: ZoneId): ComputedScoresEntity? {
        val profile = db.profileDao().get() ?: return null
        val baselines = db.baselineDao().all().associate { it.metric to BaselineEntry(it.metric, it.mean, it.sd, Instant.ofEpochSecond(it.updatedAt)) }

        val cycles = db.dailyCycleDao().recent(8)
        val wakeForDate = cycles.firstOrNull { it.date == date.toString() }?.wakeTs

        val sleepWindowStart = date.minusDays(2).atStartOfDay(zone).toInstant().epochSecond
        val sleepWindowEnd = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        val allStages = db.sleepDao().stagesInRange(sleepWindowStart, sleepWindowEnd)
        val allSessions = db.sleepDao().sessionsInRange(sleepWindowStart, sleepWindowEnd)

        val mainSleep = pickMainSleep(allSessions, allStages, date, wakeForDate, zone)
        val naps = allSessions.filter { it !in listOfNotNull(mainSleep) && (it.endTs - it.startTs) < 180L * 60 }

        val sleepMetrics = if (mainSleep != null) {
            stagesOf(mainSleep, allStages)
        } else null

        val cycleStart = wakeForDate ?: date.atStartOfDay(zone).toInstant()
        val cycleEnd = cycles.firstOrNull { it.date == date.plusDays(1).toString() }?.wakeTs
            ?: date.plusDays(1).atStartOfDay(zone).toInstant()

        val hrSamples = db.hrDao().range(cycleStart.epochSecond, cycleEnd.epochSecond)
            .map { HrSample(Instant.ofEpochSecond(it.ts), it.bpm, SourceId.HEALTH_CONNECT) }
        val workouts = db.workoutDao().range(cycleStart.epochSecond, cycleEnd.epochSecond)
            .map { Workout(it.type, Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs), it.avgHr, it.maxHr, it.calories, SourceId.HEALTH_CONNECT) }

        var strain: Double? = null
        var trimp: Double? = null
        if (hrSamples.isNotEmpty() && baselines.containsKey(BaselineKeys.RHR)) {
            val rhr = baselines[BaselineKeys.RHR]!!.mean
            val (s, t) = strainCalc.dailyStrain(hrSamples, rhr, hrMaxFor(profile), Sex.valueOf(profile.sex.uppercase()), workouts)
            strain = s; trimp = t
        }

        val strength = workouts.filter { it.isStrength() }
        val muscular = strainCalc.muscularLoad(strength, bodyWeightKg = profile.weightKg)

        var sleepPerformance: Int? = null
        var sleepEfficiency: Int? = null
        var asleepMin = 0L
        var needMin = profile.sleepNeedBaseMin.toDouble()

        if (mainSleep != null && sleepMetrics != null) {
            val sm = sleepCalc.computeMetrics(
                listOf(SleepSession(Instant.ofEpochSecond(mainSleep.startTs), Instant.ofEpochSecond(mainSleep.endTs), sleepMetrics, SourceId.HEALTH_CONNECT)) +
                    naps.map { SleepSession(Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs), stagesOf(it, allStages), SourceId.HEALTH_CONNECT) }
            )
            asleepMin = sm.asleepMin

            val recentNights = recentNightsAsleep(date, zone)
            val need = sleepCalc.sleepNeed(
                baseMin = profile.sleepNeedBaseMin.toDouble(),
                recentStrain = strain ?: 0.0,
                recentNightsAsleepMin = recentNights,
                napMin = sm.naps.sumOf { it.asleepMin }.toDouble()
            )
            needMin = need.needMin
            sleepPerformance = sleepCalc.performance(sm.asleepMin, need.needMin)
            sleepEfficiency = sleepCalc.efficiency(sm.asleepMin, sm.inBedMin)
        }

        var recovery: Int? = null
        var recoveryInputs = emptyMap<String, Double>()
        if (mainSleep != null && baselines.size >= 2) {
            val sleepStart = mainSleep.startTs
            val sleepEnd = mainSleep.endTs
            val hrvAvg = db.hrvDao().avgBetween(sleepStart, sleepEnd)
            val rhrMin = db.hrDao().minBetween(sleepStart, sleepEnd)
            val respAvg = db.respDao().avgBetween(sleepStart, sleepEnd)
            val spo2Avg = db.spo2Dao().avgBetween(sleepStart, sleepEnd)
            val skinAvg = db.skinTempDao().avgBetween(sleepStart, sleepEnd)

            val result = recoveryCalc.calculate(
                RecoveryInput(
                    hrvRmssd = hrvAvg,
                    rhr = rhrMin,
                    respRate = respAvg,
                    spo2 = spo2Avg,
                    skinTempDelta = skinAvg,
                    sleepPerformance = sleepPerformance?.toDouble()
                ),
                baselines
            )
            if (result != null) {
                recovery = result.recovery
                recoveryInputs = result.contributions.associate { it.name to it.z }
            }
        }

        val stressResult = stressCalc.calculate(
            hrSamples,
            db.hrvDao().range(cycleStart.epochSecond, cycleEnd.epochSecond)
                .map { HrvSample(Instant.ofEpochSecond(it.ts), it.rmssd, SourceId.HEALTH_CONNECT) },
            workouts,
            allSessions.map { SleepSession(Instant.ofEpochSecond(it.startTs), Instant.ofEpochSecond(it.endTs), stagesOf(it, allStages), SourceId.HEALTH_CONNECT) },
            baselines
        )

        val steps = db.stepsDao().sumBetween(cycleStart.epochSecond, cycleEnd.epochSecond) ?: 0L

        val healthspan = if (baselines.size >= 2) {
            healthCalc.paceOfAging(
                HealthspanInput(
                    sleepHours = if (asleepMin > 0) asleepMin / 60.0 else null,
                    sleepConsistency = null,
                    hrvRmssd = db.hrvDao().avgBetween(sleepStart(date, zone), Instant.now().epochSecond),
                    rhr = baselines[BaselineKeys.RHR]?.mean,
                    vo2max = null,
                    steps = steps.toDouble(),
                    zone1to5Minutes = strainCalc.zoneMinutes(hrSamples, baselines[BaselineKeys.RHR]?.mean ?: 55.0, hrMaxFor(profile)),
                    strengthSessionsPerWeek = strength.size.toDouble(),
                    sex = Sex.valueOf(profile.sex.uppercase()),
                    baselines = baselines
                )
            )
        } else null

        val inputs = buildMap {
            putAll(recoveryInputs)
            strain?.let { put("strain", it) }
            put("asleepMin", asleepMin.toDouble())
            put("needMin", needMin)
            put("steps", steps.toDouble())
        }

        return ComputedScoresEntity(
            date = date.toString(),
            recovery = recovery,
            strain = strain,
            muscularLoad = muscular,
            sleepPerformance = sleepPerformance,
            sleepEfficiency = sleepEfficiency,
            sleepConsistency = null,
            stressAvg = stressResult.avgLevel,
            healthspan = healthspan,
            inputs = json.encodeToString(inputs)
        )
    }

    private fun sleepStart(date: LocalDate, zone: ZoneId): Instant =
        date.minusDays(1).atTime(20, 0).atZone(zone).toInstant()

    private fun pickMainSleep(
        sessions: List<SleepSessionEntity>,
        stages: List<SleepStageEntity>,
        date: LocalDate,
        wakeTs: Long?,
        zone: ZoneId
    ): SleepSessionEntity? {
        val candidates = sessions.filter { it.endTs - it.startTs >= 3 * 3600 }
        val matched = if (wakeTs != null) {
            candidates.filter { kotlin.math.abs(it.endTs - wakeTs) <= 4 * 3600 }
        } else candidates
        if (matched.isEmpty()) return null
        return matched.maxByOrNull { s ->
            stages.filter { it.startTs >= s.startTs && it.endTs <= s.endTs }
                .filter { it.type != SleepStageType.AWAKE }
                .sumOf { it.endTs - it.startTs }
        }
    }

    private fun stagesOf(session: SleepSessionEntity, all: List<SleepStageEntity>) =
        all.filter { it.startTs >= session.startTs && it.endTs <= session.endTs }

    private suspend fun recentNightsAsleep(today: LocalDate, zone: ZoneId): List<Double> {
        val out = mutableListOf<Double>()
        for (i in 1..AlgorithmsConfig.SLEEP_DEBT_WINDOW) {
            val d = today.minusDays(i.toLong())
            val e = db.scoresDao().get(d.toString()) ?: continue
            val asleep = e.inputs.let { j ->
                runCatching { json.decodeFromString<Map<String, Double>>(j) }.getOrNull()?.get("asleepMin")
            }
            out.add(asleep ?: profileDefault())
        }
        return out
    }

    private suspend fun profileDefault(): Double {
        val p = db.profileDao().get() ?: return 480.0
        return p.sleepNeedBaseMin.toDouble()
    }

    suspend fun updateBaselines(date: LocalDate, zone: ZoneId) {
        val dayStart = date.atStartOfDay(zone).toInstant().epochSecond
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond

        val hrvAvg = db.hrvDao().avgBetween(dayStart - 6 * 3600, dayEnd)
        val hrLow = db.hrDao().minBetween(dayStart, dayEnd)
        val respAvg = db.respDao().avgBetween(dayStart - 6 * 3600, dayEnd)
        val spo2Avg = db.spo2Dao().avgBetween(dayStart - 6 * 3600, dayEnd)
        val skinAvg = db.skinTempDao().avgBetween(dayStart - 6 * 3600, dayEnd)

        val daytimeHr = db.hrDao().range(dayStart + 8 * 3600, dayEnd - 4 * 3600)
            .filter { it.bpm in 55..110 }
            .map { it.bpm }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val engine = BaselineEngine()
        val existing = db.baselineDao().all().associate { it.metric to BaselineEntry(it.metric, it.mean, it.sd, Instant.ofEpochSecond(it.updatedAt)) }
        val updated = engine.update(
            existing,
            BaselineEngine.Update(
                hrvRmssd = hrvAvg,
                rhr = hrLow,
                respRate = respAvg,
                spo2 = spo2Avg,
                skinTempDelta = skinAvg,
                daytimeRhr = daytimeHr
            )
        )
        db.baselineDao().upsertAll(updated.values.map { BaselineEntity(it.metric, it.mean, it.sd, it.updatedAt.epochSecond) })
    }

    suspend fun updateCycle(date: LocalDate, wakeTs: Long, nextWakeTs: Long) {
        db.dailyCycleDao().upsert(DailyCycleEntity(date.toString(), wakeTs, nextWakeTs))
    }

    fun isCalibrating(baselineCount: Int, oldestScore: LocalDate?, zone: ZoneId): Boolean {
        return baselineCount < AlgorithmsConfig.BASELINE_MIN_DAYS
    }
}
