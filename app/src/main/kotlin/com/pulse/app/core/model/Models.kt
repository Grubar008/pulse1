package com.pulse.app.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

enum class SourceId { HEALTH_CONNECT, GARMIN, ZEPP, FILE_IMPORT }

enum class MetricType {
    HR, HRV, RHR, RESP_RATE, SPO2, SKIN_TEMP, STEPS, WORKOUT, SLEEP, VO2_MAX, CALORIES
}

enum class Sex { MALE, FEMALE, OTHER }

enum class StressBand(val label: String) {
    CALM("Calm"), MILD("Mild"), MODERATE("Moderate"), HIGH("High")
}

@Serializable
enum class SleepStageType { AWAKE, LIGHT, DEEP, REM }

data class HrSample(val ts: Instant, val bpm: Double, val source: SourceId)
data class HrvSample(val ts: Instant, val rmssd: Double, val source: SourceId)
data class Spo2Sample(val ts: Instant, val percent: Double, val source: SourceId)
data class RespSample(val ts: Instant, val breathsPerMin: Double, val source: SourceId)
data class SkinTempSample(val ts: Instant, val deltaCelsius: Double, val source: SourceId)
data class StepsBucket(val start: Instant, val end: Instant, val steps: Long, val source: SourceId)

data class SleepStage(val type: SleepStageType, val start: Instant, val end: Instant) {
    val durationMin: Long get() = (end.epochSecond - start.epochSecond) / 60
}

data class SleepSession(
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStage>,
    val source: SourceId
) {
    val asleepMin: Long get() = stages.filter { it.type != SleepStageType.AWAKE }.sumOf { it.durationMin }
}

data class Workout(
    val type: String,
    val start: Instant,
    val end: Instant,
    val avgHr: Double?,
    val maxHr: Double?,
    val calories: Double?,
    val source: SourceId
) {
    val durationMin: Long get() = (end.epochSecond - start.epochSecond) / 60

    fun isStrength(): Boolean = type.lowercase().let {
        it.contains("strength") || it.contains("weight") || it.contains("resistance")
    }
}

data class DailyCycle(val date: LocalDate, val wake: Instant, val nextWake: Instant) {
    val start: Instant get() = wake
    val end: Instant get() = nextWake
}

data class TimeRange(val start: Instant, val end: Instant)

data class BaselineEntry(val metric: String, val mean: Double, val sd: Double, val updatedAt: Instant)

sealed interface SourceStatus {
    data object Connected : SourceStatus
    data object Disconnected : SourceStatus
    data object NotInstalled : SourceStatus
    data object Outdated : SourceStatus
    data object PermissionsMissing : SourceStatus
    data class Error(val message: String) : SourceStatus
}

@Serializable
enum class ValueType { BOOLEAN, SCALE, MINUTES, SERVINGS }

@Serializable
data class BehaviorDef(val id: String, val label: String, val valueType: ValueType, val isCustom: Boolean = false)

@Serializable
data class JournalEntry(val date: LocalDate, val behaviorId: String, val value: Double)

@Serializable
data class JournalEffect(
    val behaviorId: String,
    val samplesWith: Int,
    val samplesWithout: Int,
    val deltaRecovery: Double,
    val effectSize: Double,
    val pValue: Double,
    val confidence: String
)

@Serializable
data class Scores(
    val date: LocalDate,
    val recovery: Int? = null,
    val strain: Double? = null,
    val muscularLoad: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepEfficiency: Int? = null,
    val sleepConsistency: Int? = null,
    val stressAvg: Double? = null,
    val healthspan: Double? = null,
    val inputs: Map<String, Double> = emptyMap()
) {
    fun recoveryZone(): Int = when {
        recovery == null -> -1
        recovery <= 33 -> 0
        recovery <= 66 -> 1
        else -> 2
    }
}

@Serializable
data class UserProfile(
    val name: String = "",
    val age: Int = 30,
    val sex: Sex = Sex.MALE,
    val weightKg: Double = 75.0,
    val hrMax: Int = 187,
    val sleepNeedBaseMin: Int = 480,
    val useImperial: Boolean = false,
    val onboarded: Boolean = false
)

@Serializable
data class DataCoverage(val metric: MetricType, val sources: List<SourceId>, val lastSeen: Instant?)
