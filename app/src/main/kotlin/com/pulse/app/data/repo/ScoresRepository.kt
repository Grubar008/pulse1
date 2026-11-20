package com.pulse.app.data.repo

import com.pulse.app.core.model.Scores
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.db.entity.ComputedScoresEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoresRepository @Inject constructor(private val db: PulseDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun day(date: LocalDate): Flow<ComputedScoresEntity?> = flow {
        emit(db.scoresDao().get(date.toString()))
    }

    fun recentFlow(limit: Int): Flow<List<ComputedScoresEntity>> = flow {
        emit(db.scoresDao().recent(limit))
    }

    suspend fun getDay(date: LocalDate): ComputedScoresEntity? = db.scoresDao().get(date.toString())

    suspend fun recent(limit: Int): List<ComputedScoresEntity> = db.scoresDao().recent(limit)

    suspend fun since(from: LocalDate): List<ComputedScoresEntity> = db.scoresDao().since(from.toString())

    fun toScores(e: ComputedScoresEntity): Scores = Scores(
        date = LocalDate.parse(e.date),
        recovery = e.recovery,
        strain = e.strain,
        muscularLoad = e.muscularLoad,
        sleepPerformance = e.sleepPerformance,
        sleepEfficiency = e.sleepEfficiency,
        sleepConsistency = e.sleepConsistency,
        stressAvg = e.stressAvg,
        healthspan = e.healthspan,
        inputs = runCatching { json.decodeFromString<Map<String, Double>>(e.inputs) }.getOrDefault(emptyMap())
    )
}
