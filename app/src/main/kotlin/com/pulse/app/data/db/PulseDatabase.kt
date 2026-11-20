package com.pulse.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pulse.app.data.db.dao.*
import com.pulse.app.data.db.entity.*

@Database(
    entities = [
        HrSampleEntity::class,
        HrvSampleEntity::class,
        Spo2SampleEntity::class,
        RespSampleEntity::class,
        SkinTempSampleEntity::class,
        StepsBucketEntity::class,
        SleepStageEntity::class,
        SleepSessionEntity::class,
        WorkoutEntity::class,
        DailyCycleEntity::class,
        ComputedScoresEntity::class,
        BaselineEntity::class,
        JournalEntryEntity::class,
        BehaviorDefEntity::class,
        UserProfileEntity::class,
        SourceConnectionEntity::class,
        ChangesTokenEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun hrDao(): HrDao
    abstract fun hrvDao(): HrvDao
    abstract fun spo2Dao(): Spo2Dao
    abstract fun respDao(): RespDao
    abstract fun skinTempDao(): SkinTempDao
    abstract fun stepsDao(): StepsDao
    abstract fun sleepDao(): SleepDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun dailyCycleDao(): DailyCycleDao
    abstract fun scoresDao(): ScoresDao
    abstract fun baselineDao(): BaselineDao
    abstract fun journalDao(): JournalDao
    abstract fun profileDao(): ProfileDao
    abstract fun sourceDao(): SourceConnectionDao
    abstract fun changesTokenDao(): ChangesTokenDao
    abstract fun wipeDao(): WipeDao
}
