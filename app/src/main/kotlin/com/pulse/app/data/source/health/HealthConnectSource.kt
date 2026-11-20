package com.pulse.app.data.source.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.HrvSample
import com.pulse.app.core.model.MetricType
import com.pulse.app.core.model.RespSample
import com.pulse.app.core.model.SleepSession
import com.pulse.app.core.model.SleepStage
import com.pulse.app.core.model.SleepStageType
import com.pulse.app.core.model.Spo2Sample
import com.pulse.app.core.model.SourceId
import com.pulse.app.core.model.SourceStatus
import com.pulse.app.core.model.StepsBucket
import com.pulse.app.core.model.TimeRange
import com.pulse.app.core.model.Workout
import com.pulse.app.data.source.DataSource
import java.time.Instant
import java.time.LocalDate

class HealthConnectSource(private val context: Context) : DataSource {

    override val id: SourceId = SourceId.HEALTH_CONNECT

    override val availableMetrics: Set<MetricType> = setOf(
        MetricType.HR, MetricType.HRV, MetricType.RHR, MetricType.RESP_RATE, MetricType.SPO2,
        MetricType.SKIN_TEMP, MetricType.STEPS, MetricType.WORKOUT, MetricType.SLEEP,
        MetricType.VO2_MAX, MetricType.CALORIES
    )

    object HealthPerms {
        val RECORD_TYPES = setOf(
            HeartRateRecord::class, RestingHeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class, SleepSessionRecord::class,
            OxygenSaturationRecord::class, RespiratoryRateRecord::class,
            SkinTemperatureRecord::class, StepsRecord::class,
            ExerciseSessionRecord::class, Vo2MaxRecord::class,
            ActiveCaloriesBurnedRecord::class
        )

        val READ_PERMISSIONS: Set<String> = RECORD_TYPES.map { HealthPermission.getReadPermission(it) }.toSet() +
            setOf(
                "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
                "android.permission.health.READ_HEALTH_DATA_HISTORY"
            )
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null

    fun availabilityStatus(): Int = HealthConnectClient.getSdkStatus(context)

    override suspend fun status(): SourceStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                val dataPerms = granted.filterTo(mutableSetOf()) { it in HealthPerms.READ_PERMISSIONS }
                if (dataPerms.size >= HealthPerms.RECORD_TYPES.size - 2) SourceStatus.Connected
                else SourceStatus.PermissionsMissing
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> SourceStatus.Outdated
            else -> SourceStatus.NotInstalled
        }

    suspend fun grantedPermissions(): Set<String> {
        val client = clientOrNull() ?: return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    private suspend fun <T : Record> readRecords(recordClass: Class<T>, range: TimeRange): List<T> {
        val client = clientOrNull() ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = recordClass.kotlin,
            timeRangeFilter = TimeRangeFilter.between(range.start, range.end),
            pageSize = 5000
        )
        return client.readRecords(request).records
    }

    override suspend fun readHeartRate(range: TimeRange): List<HrSample> =
        readRecords(HeartRateRecord::class.java, range).flatMap { rec ->
            rec.samples.map { HrSample(it.time, it.beatsPerMinute.toDouble(), SourceId.HEALTH_CONNECT) }
        }

    override suspend fun readHrv(range: TimeRange): List<HrvSample> =
        readRecords(HeartRateVariabilityRmssdRecord::class.java, range).map {
            HrvSample(it.time, it.heartRateVariabilityMillis, SourceId.HEALTH_CONNECT)
        }

    override suspend fun readRestingHr(range: TimeRange): List<HrSample> =
        readRecords(RestingHeartRateRecord::class.java, range).map {
            HrSample(it.time, it.beatsPerMinute.toDouble(), SourceId.HEALTH_CONNECT)
        }

    override suspend fun readSleepSessions(range: TimeRange): List<SleepSession> =
        readRecords(SleepSessionRecord::class.java, range).map { rec ->
            SleepSession(
                start = rec.startTime,
                end = rec.endTime,
                stages = rec.stages.map { SleepStage(mapStage(it.stage), it.startTime, it.endTime) },
                source = SourceId.HEALTH_CONNECT
            )
        }

    private fun mapStage(stage: Int): SleepStageType = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        else -> SleepStageType.LIGHT
    }

    override suspend fun readSteps(range: TimeRange): List<StepsBucket> =
        readRecords(StepsRecord::class.java, range).map {
            StepsBucket(it.startTime, it.endTime, it.count, SourceId.HEALTH_CONNECT)
        }

    override suspend fun readExercise(range: TimeRange): List<Workout> =
        readRecords(ExerciseSessionRecord::class.java, range).map { rec ->
            Workout(
                type = exerciseName(rec.exerciseType),
                start = rec.startTime,
                end = rec.endTime,
                avgHr = null,
                maxHr = null,
                calories = null,
                source = SourceId.HEALTH_CONNECT
            )
        }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "biking"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "strength_training"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "rowing"
        else -> "workout"
    }

    override suspend fun readSpO2(range: TimeRange): List<Spo2Sample> =
        readRecords(OxygenSaturationRecord::class.java, range).map {
            Spo2Sample(it.time, it.percentage.value, SourceId.HEALTH_CONNECT)
        }

    override suspend fun readRespiratoryRate(range: TimeRange): List<RespSample> =
        readRecords(RespiratoryRateRecord::class.java, range).map {
            RespSample(it.time, it.rate, SourceId.HEALTH_CONNECT)
        }

    override suspend fun readSkinTemp(range: TimeRange): List<com.pulse.app.core.model.SkinTempSample> =
        readRecords(SkinTemperatureRecord::class.java, range).flatMap { rec ->
            rec.deltas.map {
                com.pulse.app.core.model.SkinTempSample(it.time, it.delta.inCelsius, SourceId.HEALTH_CONNECT)
            }
        }

    override suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>> =
        readRecords(Vo2MaxRecord::class.java, range).map {
            it.time to it.vo2MillilitersPerMinuteKilogram
        }

    suspend fun changesToken(): String? {
        val client = clientOrNull() ?: return null
        return client.getChangesToken(
            ChangesTokenRequest(HealthPerms.RECORD_TYPES)
        )
    }

    suspend fun readChanges(token: String): ChangesResult {
        val client = clientOrNull() ?: return ChangesResult(emptyList(), token, false)
        val response = client.getChanges(token)
        val upserted = response.changes.filterIsInstance<androidx.health.connect.client.changes.UpsertionChange>()
            .map { it.record }
        return ChangesResult(upserted, response.nextChangesToken, response.hasMore)
    }

    data class ChangesResult(val upserted: List<Record>, val nextToken: String, val hasMore: Boolean)

    companion object {
        fun computeEarliestBackfillStart(now: Instant): Instant =
            now.minusSeconds(60L * 60 * 24 * 365)
    }
}
