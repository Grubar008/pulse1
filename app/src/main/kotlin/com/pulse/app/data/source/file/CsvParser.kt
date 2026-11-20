package com.pulse.app.data.source.file

import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.HrvSample
import com.pulse.app.core.model.RespSample
import com.pulse.app.core.model.SleepSession
import com.pulse.app.core.model.SleepStage
import com.pulse.app.core.model.SleepStageType
import com.pulse.app.core.model.Spo2Sample
import com.pulse.app.core.model.SourceId
import com.pulse.app.core.model.StepsBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CsvImported(
    val hr: List<HrSample>,
    val hrv: List<HrvSample>,
    val spo2: List<Spo2Sample>,
    val resp: List<RespSample>,
    val steps: List<StepsBucket>,
    val sleep: List<SleepSession>
)

class CsvParser(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun parse(content: String): CsvImported {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvImported(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        val header = lines.first().split(",", ";").map { it.trim().lowercase() }
        val hr = mutableListOf<HrSample>()
        val hrv = mutableListOf<HrvSample>()
        val spo2 = mutableListOf<Spo2Sample>()
        val resp = mutableListOf<RespSample>()
        val steps = mutableListOf<StepsBucket>()
        val sleep = mutableListOf<SleepSession>()

        val tsIdx = header.indexOfFirst { it in setOf("timestamp", "time", "ts", "date", "datetime") }
        val heartIdx = header.indexOfFirst { it.contains("heart") || it == "hr" || it == "bpm" }
        val hrvIdx = header.indexOfFirst { it.contains("hrv") || it.contains("rmssd") }
        val spo2Idx = header.indexOfFirst { it.contains("spo2") || it.contains("oxygen") || it.contains("o2") }
        val respIdx = header.indexOfFirst { it.contains("resp") }
        val stepsIdx = header.indexOfFirst { it.contains("step") }
        val sleepStartIdx = header.indexOfFirst { it.contains("sleep") && it.contains("start") }
        val sleepEndIdx = header.indexOfFirst { it.contains("sleep") && it.contains("end") }
        val deepIdx = header.indexOfFirst { it.contains("deep") }
        val lightIdx = header.indexOfFirst { it.contains("light") }
        val remIdx = header.indexOfFirst { it.contains("rem") }
        val awakeIdx = header.indexOfFirst { it.contains("awake") }

        for (line in lines.drop(1)) {
            val cells = line.split(",", ";").map { it.trim() }
            if (cells.size < 2) continue

            sleepStartIdx.takeIf { it >= 0 }
            val ts = if (tsIdx >= 0) parseTime(cells.getOrNull(tsIdx)) else null

            if (sleepStartIdx >= 0 && sleepEndIdx >= 0) {
                val start = parseTime(cells.getOrNull(sleepStartIdx))
                val end = parseTime(cells.getOrNull(sleepEndIdx))
                if (start != null && end != null) {
                    val deepVal = cells.getOrNull(deepIdx)?.toDoubleOrNull() ?: 0.0
                    val lightVal = cells.getOrNull(lightIdx)?.toDoubleOrNull() ?: 0.0
                    val remVal = cells.getOrNull(remIdx)?.toDoubleOrNull() ?: 0.0
                    val awakeVal = cells.getOrNull(awakeIdx)?.toDoubleOrNull() ?: 0.0
                    val stages = buildList {
                        var t = start
                        fun stage(type: SleepStageType, minutes: Double) {
                            if (minutes > 0) {
                                val endSt = t.plusSeconds((minutes * 60).toLong())
                                add(SleepStage(type, t, endSt))
                                t = endSt
                            }
                        }
                        stage(SleepStageType.DEEP, deepVal)
                        stage(SleepStageType.LIGHT, lightVal)
                        stage(SleepStageType.REM, remVal)
                        stage(SleepStageType.AWAKE, awakeVal)
                    }
                    sleep.add(SleepSession(start, end, stages, SourceId.FILE_IMPORT))
                }
                continue
            }

            var t = ts ?: continue
            heartIdx.takeIf { it >= 0 }?.let { idx ->
                cells.getOrNull(idx)?.toDoubleOrNull()?.let { hr.add(HrSample(t, it, SourceId.FILE_IMPORT)) }
            }
            hrvIdx.takeIf { it >= 0 }?.let { idx ->
                cells.getOrNull(idx)?.toDoubleOrNull()?.let { hrv.add(HrvSample(t, it, SourceId.FILE_IMPORT)) }
            }
            spo2Idx.takeIf { it >= 0 }?.let { idx ->
                cells.getOrNull(idx)?.toDoubleOrNull()?.let { spo2.add(Spo2Sample(t, it, SourceId.FILE_IMPORT)) }
            }
            respIdx.takeIf { it >= 0 }?.let { idx ->
                cells.getOrNull(idx)?.toDoubleOrNull()?.let { resp.add(RespSample(t, it, SourceId.FILE_IMPORT)) }
            }
            stepsIdx.takeIf { it >= 0 }?.let { idx ->
                cells.getOrNull(idx)?.toDoubleOrNull()?.let { v ->
                    steps.add(StepsBucket(t, t.plusSeconds(3600), v.toLong(), SourceId.FILE_IMPORT))
                }
            }
        }

        return CsvImported(hr, hrv, spo2, resp, steps, sleep)
    }

    private fun parseTime(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { epoch ->
            return if (epoch > 100_000_000_000L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        }
        for (pattern in FORMATS) {
            try {
                return LocalDate.parse(raw.take(10), pattern).atTime(12, 0).atZone(zone).toInstant()
            } catch (_: Exception) { }
            try {
                return java.time.LocalDateTime.parse(raw, pattern).atZone(zone).toInstant()
            } catch (_: Exception) { }
        }
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        val FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    }
}
