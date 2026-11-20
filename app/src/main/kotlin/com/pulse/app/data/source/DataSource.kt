package com.pulse.app.data.source

import com.pulse.app.core.model.*

interface DataSource {
    val id: SourceId
    val availableMetrics: Set<MetricType>

    suspend fun status(): SourceStatus

    suspend fun readHeartRate(range: TimeRange): List<HrSample>
    suspend fun readHrv(range: TimeRange): List<HrvSample>
    suspend fun readRestingHr(range: TimeRange): List<HrSample>
    suspend fun readSleepSessions(range: TimeRange): List<SleepSession>
    suspend fun readSteps(range: TimeRange): List<StepsBucket>
    suspend fun readExercise(range: TimeRange): List<Workout>
    suspend fun readSpO2(range: TimeRange): List<Spo2Sample>
    suspend fun readRespiratoryRate(range: TimeRange): List<RespSample>
    suspend fun readSkinTemp(range: TimeRange): List<SkinTempSample>
    suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>>
}
