package com.pulse.app.data.source.file

import com.pulse.app.core.model.*
import com.pulse.app.data.source.DataSource
import java.io.InputStream
import java.time.Instant

class FileImportSource : DataSource {

    override val id: SourceId = SourceId.FILE_IMPORT
    override val availableMetrics: Set<MetricType> = setOf(
        MetricType.HR, MetricType.HRV, MetricType.SPO2, MetricType.RESP_RATE,
        MetricType.STEPS, MetricType.SLEEP, MetricType.WORKOUT
    )

    override suspend fun status(): SourceStatus = SourceStatus.Connected

    fun importFit(stream: InputStream): FitParser.FitData = FitParser().parse(stream)

    fun importCsv(content: String): CsvImported = CsvParser().parse(content)

    fun importGarminGdpr(fitBytes: List<ByteArray>, csvFiles: List<String>): CsvImported {
        val hr = mutableListOf<HrSample>()
        val workouts = mutableListOf<Workout>()
        for (b in fitBytes) {
            val parsed = FitParser().parse(b.inputStream())
            hr.addAll(parsed.hr)
            workouts.addAll(parsed.sessions)
        }
        val agg = csvFiles.fold(CsvImported(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())) { acc, csv ->
            val parsed = parse(csv)
            CsvImported(
                acc.hr + parsed.hr, acc.hrv + parsed.hrv, acc.spo2 + parsed.spo2,
                acc.resp + parsed.resp, acc.steps + parsed.steps, acc.sleep + parsed.sleep
            )
        }
        return agg
    }

    private fun parse(csv: String): CsvImported = CsvParser().parse(csv)

    override suspend fun readHeartRate(range: TimeRange): List<HrSample> = emptyList()
    override suspend fun readHrv(range: TimeRange): List<HrvSample> = emptyList()
    override suspend fun readRestingHr(range: TimeRange): List<HrSample> = emptyList()
    override suspend fun readSleepSessions(range: TimeRange): List<SleepSession> = emptyList()
    override suspend fun readSteps(range: TimeRange): List<StepsBucket> = emptyList()
    override suspend fun readExercise(range: TimeRange): List<Workout> = emptyList()
    override suspend fun readSpO2(range: TimeRange): List<Spo2Sample> = emptyList()
    override suspend fun readRespiratoryRate(range: TimeRange): List<RespSample> = emptyList()
    override suspend fun readSkinTemp(range: TimeRange): List<SkinTempSample> = emptyList()
    override suspend fun readVo2Max(range: TimeRange): List<Pair<Instant, Double>> = emptyList()
}
