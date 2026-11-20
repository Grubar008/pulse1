package com.pulse.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulse.app.data.db.entity.*

@Dao
interface HrDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<HrSampleEntity>)

    @Query("SELECT * FROM hr_sample WHERE ts >= :from AND ts < :until ORDER BY ts")
    suspend fun range(from: Long, until: Long): List<HrSampleEntity>

    @Query("SELECT MIN(bpm) FROM hr_sample WHERE ts >= :from AND ts < :until")
    suspend fun minBetween(from: Long, until: Long): Double?

    @Query("SELECT AVG(bpm) FROM (SELECT bpm FROM hr_sample WHERE ts >= :from AND ts < :until LIMIT 5)")
    suspend fun avgLowestAround(from: Long, until: Long): Double?

    @Query("DELETE FROM hr_sample WHERE ts >= :from AND ts < :until AND source = :source")
    suspend fun deleteRange(from: Long, until: Long, source: String)
}

@Dao
interface HrvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<HrvSampleEntity>)

    @Query("SELECT * FROM hrv_sample WHERE ts >= :from AND ts < :until ORDER BY ts")
    suspend fun range(from: Long, until: Long): List<HrvSampleEntity>

    @Query("SELECT AVG(rmssd) FROM hrv_sample WHERE ts >= :from AND ts < :until")
    suspend fun avgBetween(from: Long, until: Long): Double?
}

@Dao
interface Spo2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<Spo2SampleEntity>)

    @Query("SELECT * FROM spo2_sample WHERE ts >= :from AND ts < :until ORDER BY ts")
    suspend fun range(from: Long, until: Long): List<Spo2SampleEntity>

    @Query("SELECT AVG(percent) FROM spo2_sample WHERE ts >= :from AND ts < :until")
    suspend fun avgBetween(from: Long, until: Long): Double?
}

@Dao
interface RespDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<RespSampleEntity>)

    @Query("SELECT * FROM resp_sample WHERE ts >= :from AND ts < :until ORDER BY ts")
    suspend fun range(from: Long, until: Long): List<RespSampleEntity>

    @Query("SELECT AVG(breathsPerMin) FROM resp_sample WHERE ts >= :from AND ts < :until")
    suspend fun avgBetween(from: Long, until: Long): Double?
}

@Dao
interface SkinTempDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<SkinTempSampleEntity>)

    @Query("SELECT * FROM skin_temp_sample WHERE ts >= :from AND ts < :until ORDER BY ts")
    suspend fun range(from: Long, until: Long): List<SkinTempSampleEntity>

    @Query("SELECT AVG(deltaCelsius) FROM skin_temp_sample WHERE ts >= :from AND ts < :until")
    suspend fun avgBetween(from: Long, until: Long): Double?
}

@Dao
interface StepsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(samples: List<StepsBucketEntity>)

    @Query("SELECT * FROM steps_bucket WHERE startTs >= :from AND startTs < :until ORDER BY startTs")
    suspend fun range(from: Long, until: Long): List<StepsBucketEntity>

    @Query("SELECT SUM(steps) FROM steps_bucket WHERE startTs >= :from AND startTs < :until")
    suspend fun sumBetween(from: Long, until: Long): Long?
}

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<SleepSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStages(stages: List<SleepStageEntity>)

    @Query("SELECT * FROM sleep_session WHERE startTs >= :from AND startTs < :until ORDER BY startTs")
    suspend fun sessionsInRange(from: Long, until: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stage WHERE startTs >= :from AND endTs <= :until ORDER BY startTs")
    suspend fun stagesInRange(from: Long, until: Long): List<SleepStageEntity>

    @Query("SELECT * FROM sleep_stage WHERE startTs >= :from AND startTs < :until ORDER BY startTs")
    suspend fun stagesStartInRange(from: Long, until: Long): List<SleepStageEntity>

    @Query("DELETE FROM sleep_session WHERE startTs >= :from AND endTs <= :until AND source = :source")
    suspend fun deleteRange(from: Long, until: Long, source: String)
}

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Query("SELECT * FROM workout WHERE startTs >= :from AND startTs < :until ORDER BY startTs")
    suspend fun range(from: Long, until: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workout ORDER BY startTs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<WorkoutEntity>
}

@Dao
interface DailyCycleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cycle: DailyCycleEntity)

    @Query("SELECT * FROM daily_cycle WHERE date = :date")
    suspend fun get(date: String): DailyCycleEntity?

    @Query("SELECT * FROM daily_cycle ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DailyCycleEntity>
}

@Dao
interface ScoresDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scores: ComputedScoresEntity)

    @Query("SELECT * FROM computed_scores WHERE date = :date")
    suspend fun get(date: String): ComputedScoresEntity?

    @Query("SELECT * FROM computed_scores WHERE date >= :from ORDER BY date")
    suspend fun since(from: String): List<ComputedScoresEntity>

    @Query("SELECT * FROM computed_scores ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ComputedScoresEntity>
}

@Dao
interface BaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BaselineEntity>)

    @Query("SELECT * FROM baseline")
    suspend fun all(): List<BaselineEntity>

    @Query("DELETE FROM baseline")
    suspend fun clear()
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("SELECT * FROM journal_entry WHERE date = :date")
    suspend fun forDate(date: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entry")
    suspend fun all(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBehaviors(defs: List<BehaviorDefEntity>)

    @Query("SELECT * FROM behavior_def")
    suspend fun behaviors(): List<BehaviorDefEntity>

    @Query("DELETE FROM journal_entry WHERE date = :date AND behaviorId = :behaviorId")
    suspend fun delete(date: String, behaviorId: String)
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?
}

@Dao
interface SourceConnectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: SourceConnectionEntity)

    @Query("SELECT * FROM source_connection")
    suspend fun all(): List<SourceConnectionEntity>

    @Query("SELECT * FROM source_connection WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): SourceConnectionEntity?

    @Query("DELETE FROM source_connection WHERE sourceId = :sourceId")
    suspend fun delete(sourceId: String)
}

@Dao
interface ChangesTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: ChangesTokenEntity)

    @Query("SELECT * FROM changes_token WHERE sourceId = :sourceId AND dataType = :dataType")
    suspend fun get(sourceId: String, dataType: String): ChangesTokenEntity?
}

@Dao
interface WipeDao {
    @Query("DELETE FROM hr_sample")
    suspend fun wipeHr()
    @Query("DELETE FROM hrv_sample")
    suspend fun wipeHrv()
    @Query("DELETE FROM spo2_sample")
    suspend fun wipeSpo2()
    @Query("DELETE FROM resp_sample")
    suspend fun wipeResp()
    @Query("DELETE FROM skin_temp_sample")
    suspend fun wipeSkin()
    @Query("DELETE FROM steps_bucket")
    suspend fun wipeSteps()
    @Query("DELETE FROM sleep_session")
    suspend fun wipeSleepSessions()
    @Query("DELETE FROM sleep_stage")
    suspend fun wipeSleepStages()
    @Query("DELETE FROM workout")
    suspend fun wipeWorkouts()
    @Query("DELETE FROM daily_cycle")
    suspend fun wipeCycles()
    @Query("DELETE FROM computed_scores")
    suspend fun wipeScores()
    @Query("DELETE FROM baseline")
    suspend fun wipeBaselines()
    @Query("DELETE FROM journal_entry")
    suspend fun wipeJournal()
    @Query("DELETE FROM source_connection")
    suspend fun wipeSources()
    @Query("DELETE FROM changes_token")
    suspend fun wipeTokens()
}
