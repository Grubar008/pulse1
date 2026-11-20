package com.pulse.app.data.repo

import com.pulse.app.core.model.MetricType
import com.pulse.app.core.model.SourceId
import com.pulse.app.core.model.TimeRange
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.db.entity.*
import com.pulse.app.data.source.DataSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngestRepository @Inject constructor(private val db: PulseDatabase) {

    suspend fun availableMetricsPerSource(source: DataSource): Set<MetricType> {
        val conn = db.sourceDao().get(source.id.name)
        return if (conn?.enabled == true) source.availableMetrics else emptySet()
    }

    suspend fun ingest(source: DataSource, range: TimeRange): IngestStats {
        val enabled = db.sourceDao().get(source.id.name)?.enabled ?: false
        if (!enabled) return IngestStats(0, 0, 0, 0, 0, 0)

        val hr = source.readHeartRate(range)
        val hrv = source.readHrv(range)
        val spo2 = source.readSpO2(range)
        val resp = source.readRespiratoryRate(range)
        val skin = source.readSkinTemp(range)
        val steps = source.readSteps(range)
        val workouts = source.readExercise(range)
        val sleeps = source.readSleepSessions(range)

        val src = source.id.name

        db.hrDao().upsertAll(hr.map { HrSampleEntity(it.ts.epochSecond, it.bpm, src, remoteId(it.ts, src)) })
        db.hrvDao().upsertAll(hrv.map { HrvSampleEntity(it.ts.epochSecond, it.rmssd, src, remoteId(it.ts, src)) })
        db.spo2Dao().upsertAll(spo2.map { Spo2SampleEntity(it.ts.epochSecond, it.percent, src) })
        db.respDao().upsertAll(resp.map { RespSampleEntity(it.ts.epochSecond, it.breathsPerMin, src) })
        db.skinTempDao().upsertAll(skin.map { SkinTempSampleEntity(it.ts.epochSecond, it.deltaCelsius, src) })
        db.stepsDao().upsertAll(steps.map { StepsBucketEntity(it.start.epochSecond, it.end.epochSecond, it.steps, src) })
        db.workoutDao().upsertAll(workouts.map {
            WorkoutEntity(
                startTs = it.start.epochSecond, endTs = it.end.epochSecond, type = it.type,
                avgHr = it.avgHr, maxHr = it.maxHr, calories = it.calories, source = src
            )
        })
        for (s in sleeps) {
            db.sleepDao().upsertSessions(listOf(SleepSessionEntity(s.start.epochSecond, s.end.epochSecond, src)))
            db.sleepDao().upsertStages(s.stages.map { SleepStageEntity(it.start.epochSecond, it.end.epochSecond, it.type, src) })
        }

        val touched = mutableSetOf<Instant>()
        hr.forEach { touched.add(it.ts) }
        hrv.forEach { touched.add(it.ts) }
        spo2.forEach { touched.add(it.ts) }
        sleeps.forEach { touched.add(it.end) }
        workouts.forEach { touched.add(it.end) }

        db.sourceDao().upsert(
            SourceConnectionEntity(
                sourceId = src,
                enabled = true,
                priority = db.sourceDao().get(src)?.priority ?: 99,
                lastSyncTs = range.end.epochSecond,
                lastError = null
            )
        )

        return IngestStats(
            hr = hr.size, hrv = hrv.size, spo2 = spo2.size,
            steps = steps.size, workouts = workouts.size, sleepSessions = sleeps.size
        )
    }

    private fun remoteId(ts: Instant, src: String): String = "$src-${ts.epochSecond}"

    suspend fun connect(sourceId: SourceId, priority: Int) {
        db.sourceDao().upsert(
            SourceConnectionEntity(
                sourceId = sourceId.name, enabled = true,
                priority = priority, lastSyncTs = null, lastError = null
            )
        )
    }

    suspend fun disconnect(sourceId: SourceId) {
        db.sourceDao().upsert(
            SourceConnectionEntity(sourceId = sourceId.name, enabled = false, priority = 99, lastSyncTs = null, lastError = null)
        )
        val now = Instant.now().epochSecond
        db.hrDao().deleteRange(0, now + 1, sourceId.name)
    }

    suspend fun allConnections(): List<SourceConnectionEntity> = db.sourceDao().all()

    suspend fun recordError(sourceId: SourceId, message: String) {
        val conn = db.sourceDao().get(sourceId.name) ?: return
        db.sourceDao().upsert(conn.copy(lastError = message))
    }
}

data class IngestStats(
    val hr: Int, val hrv: Int, val spo2: Int,
    val steps: Int, val workouts: Int, val sleepSessions: Int
)
