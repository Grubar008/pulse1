package com.pulse.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pulse.app.core.model.SleepStageType
import com.pulse.app.core.model.SourceId

@Entity(tableName = "hr_sample", primaryKeys = ["ts", "source"])
data class HrSampleEntity(
    val ts: Long,
    val bpm: Double,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "hrv_sample", primaryKeys = ["ts", "source"])
data class HrvSampleEntity(
    val ts: Long,
    val rmssd: Double,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "spo2_sample", primaryKeys = ["ts", "source"])
data class Spo2SampleEntity(
    val ts: Long,
    val percent: Double,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "resp_sample", primaryKeys = ["ts", "source"])
data class RespSampleEntity(
    val ts: Long,
    val breathsPerMin: Double,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "skin_temp_sample", primaryKeys = ["ts", "source"])
data class SkinTempSampleEntity(
    val ts: Long,
    val deltaCelsius: Double,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "steps_bucket", primaryKeys = ["startTs", "source"])
data class StepsBucketEntity(
    val startTs: Long,
    val endTs: Long,
    val steps: Long,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "sleep_stage", primaryKeys = ["startTs", "source", "type"])
data class SleepStageEntity(
    val startTs: Long,
    val endTs: Long,
    val type: SleepStageType,
    val source: String
)

@Entity(tableName = "sleep_session", primaryKeys = ["startTs", "source"])
data class SleepSessionEntity(
    val startTs: Long,
    val endTs: Long,
    val source: String,
    val remoteId: String? = null
)

@Entity(tableName = "workout", primaryKeys = ["startTs", "source"])
data class WorkoutEntity(
    val startTs: Long,
    val endTs: Long,
    val type: String,
    val avgHr: Double?,
    val maxHr: Double?,
    val calories: Double?,
    val source: String,
    val remoteId: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val loadKg: Double? = null
)

@Entity(tableName = "daily_cycle")
data class DailyCycleEntity(
    @PrimaryKey val date: String,
    val wakeTs: Long,
    val nextWakeTs: Long
)

@Entity(tableName = "computed_scores")
data class ComputedScoresEntity(
    @PrimaryKey val date: String,
    val recovery: Int?,
    val strain: Double?,
    val muscularLoad: Double?,
    val sleepPerformance: Int?,
    val sleepEfficiency: Int?,
    val sleepConsistency: Int?,
    val stressAvg: Double?,
    val healthspan: Double?,
    val inputs: String
)

@Entity(tableName = "baseline")
data class BaselineEntity(
    @PrimaryKey val metric: String,
    val mean: Double,
    val sd: Double,
    val updatedAt: Long
)

@Entity(tableName = "journal_entry", primaryKeys = ["date", "behaviorId"])
data class JournalEntryEntity(
    val date: String,
    val behaviorId: String,
    val value: Double
)

@Entity(tableName = "behavior_def")
data class BehaviorDefEntity(
    @PrimaryKey val id: String,
    val label: String,
    val valueType: String,
    val isCustom: Boolean
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val age: Int,
    val sex: String,
    val weightKg: Double,
    val hrMax: Int,
    val sleepNeedBaseMin: Int,
    val useImperial: Boolean,
    val onboarded: Boolean
)

@Entity(tableName = "source_connection")
data class SourceConnectionEntity(
    @PrimaryKey val sourceId: String,
    val enabled: Boolean,
    val priority: Int,
    val lastSyncTs: Long?,
    val lastError: String?
)

@Entity(tableName = "changes_token", primaryKeys = ["sourceId", "dataType"])
data class ChangesTokenEntity(
    val sourceId: String,
    val dataType: String,
    val token: String
)
