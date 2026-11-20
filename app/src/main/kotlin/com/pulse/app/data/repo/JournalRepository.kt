package com.pulse.app.data.repo

import com.pulse.app.core.model.BehaviorDef
import com.pulse.app.core.model.BehaviorCatalog
import com.pulse.app.core.model.JournalEffect
import com.pulse.app.core.model.JournalEntry
import com.pulse.app.core.model.ValueType
import com.pulse.app.core.algorithm.JournalEngine
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.db.entity.BehaviorDefEntity
import com.pulse.app.data.db.entity.JournalEntryEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(private val db: PulseDatabase) {

    private val engine = JournalEngine()

    suspend fun behaviors(): List<BehaviorDef> {
        val dbDefs = db.journalDao().behaviors()
        return BehaviorCatalog.all(dbDefs.map { BehaviorDef(it.id, it.label, runCatching { ValueType.valueOf(it.valueType) }.getOrDefault(ValueType.BOOLEAN), it.isCustom) })
    }

    suspend fun addCustomBehavior(label: String, valueType: ValueType): BehaviorDef {
        val id = "custom_" + label.lowercase().replace(" ", "_") + "_" + (db.journalDao().behaviors().size)
        val def = BehaviorDef(id, label, valueType, isCustom = true)
        db.journalDao().upsertBehaviors(listOf(BehaviorDefEntity(def.id, def.label, def.valueType.name, true)))
        return def
    }

    suspend fun save(entry: JournalEntry) {
        db.journalDao().upsert(JournalEntryEntity(entry.date.toString(), entry.behaviorId, entry.value))
    }

    suspend fun remove(date: LocalDate, behaviorId: String) {
        db.journalDao().delete(date.toString(), behaviorId)
    }

    suspend fun forDate(date: LocalDate): List<JournalEntry> =
        db.journalDao().forDate(date.toString()).map { JournalEntry(date, it.behaviorId, it.value) }

    suspend fun allEntries(): List<JournalEntry> =
        db.journalDao().all().map { JournalEntry(java.time.LocalDate.parse(it.date), it.behaviorId, it.value) }

    suspend fun effects(): List<JournalEffect> {
        val scores = db.scoresDao().since(java.time.LocalDate.now().minusDays(180).toString())
            .associate { java.time.LocalDate.parse(it.date) to ScoresRepository(db).toScores(it) }
        return engine.analyze(allEntries(), scores)
    }
}
