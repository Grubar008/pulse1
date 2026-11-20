package com.pulse.app.work

import com.pulse.app.core.model.SourceId
import com.pulse.app.core.model.TimeRange
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.prefs.PulseSettings
import com.pulse.app.data.repo.IngestRepository
import com.pulse.app.data.repo.ScoreEngine
import com.pulse.app.data.source.DataSource
import com.pulse.app.data.source.mock.MockDataSource
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    private val db: PulseDatabase,
    private val settings: PulseSettings,
    private val ingest: IngestRepository,
    private val engine: ScoreEngine
) {

    data class SyncOutcome(
        val sourcesSynced: Int,
        val daysRecomputed: Int,
        val recoveryToday: Int?,
        val demoMode: Boolean
    )

    suspend fun sync(sources: List<DataSource>): SyncOutcome {
        val cfg = settings.settings.first()
        val zone = ZoneId.systemDefault()
        var synced = 0
        val touchedDays = mutableSetOf<LocalDate>()

        if (cfg.demoMode) {
            ingestDemo(zone, touchedDays)
        } else {
            for (source in sources) {
                try {
                    val lastSync = db.sourceDao().get(source.id.name)?.lastSyncTs
                    val fromEpoch = lastSync?.minus(3600) ?: Instant.now().epochSecond - 32L * 24 * 3600
                    val range = TimeRange(Instant.ofEpochSecond(fromEpoch), Instant.now())
                    val stats = ingest.ingest(source, range)
                    if (stats.hr + stats.hrv + stats.sleepSessions + stats.workouts > 0) synced++
                    collectTouched(range, zone, touchedDays)
                } catch (e: Exception) {
                    ingest.recordError(source.id, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        for (d in touchedDays.sorted()) {
            recomputeDay(d, zone)
        }
        val today = LocalDate.now(zone)
        if (touchedDays.isEmpty()) recomputeDay(today.minusDays(1), zone)

        ensureCycles(zone)

        val todayScores = db.scoresDao().get(today.toString())
        return SyncOutcome(
            sourcesSynced = synced,
            daysRecomputed = touchedDays.size,
            recoveryToday = todayScores?.recovery,
            demoMode = cfg.demoMode
        )
    }

    private fun collectTouched(range: TimeRange, zone: ZoneId, out: MutableSet<LocalDate>) {
        var d = LocalDate.ofInstant(range.start.minusSeconds(86400), zone)
        val end = LocalDate.ofInstant(range.end, zone)
        while (d <= end && out.size < 34) {
            out.add(d)
            d = d.plusDays(1)
        }
    }

    private suspend fun ingestDemo(zone: ZoneId, touched: MutableSet<LocalDate>) {
        if (db.scoresDao().recent(1).isNotEmpty()) return
        val mock = MockDataSource()
        val range = TimeRange(Instant.now().minusSeconds(31L * 86400), Instant.now())
        val conn = db.sourceDao().get(SourceId.HEALTH_CONNECT.name)
        if (conn == null || !conn.enabled) {
            ingest.connect(SourceId.HEALTH_CONNECT, 1)
        }
        ingest.ingest(mock, range)
        var d = LocalDate.ofInstant(range.start, zone)
        while (d <= LocalDate.now(zone)) {
            touched.add(d)
            engine.updateBaselines(d, zone)
            d = d.plusDays(1)
        }
    }

    suspend fun recomputeDay(date: LocalDate, zone: ZoneId) {
        engine.updateBaselines(date, zone)
        engine.computeDay(date, zone)?.let { db.scoresDao().upsert(it) }
    }

    suspend fun ensureCycles(zone: ZoneId) {
        val sessions = db.sleepDao().sessionsInRange(
            Instant.now().minusSeconds(10L * 86400).epochSecond,
            Instant.now().plusSeconds(86400).epochSecond
        )
        for (s in sessions) {
            if (s.endTs - s.startTs >= 3 * 3600) {
                val wakeDate = LocalDate.ofInstant(Instant.ofEpochSecond(s.endTs), zone)
                engine.updateCycle(wakeDate, s.startTs, s.endTs)
            }
        }
    }
}
