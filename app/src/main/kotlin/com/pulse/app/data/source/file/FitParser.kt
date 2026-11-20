package com.pulse.app.data.source.file

import com.pulse.app.core.model.HrSample
import com.pulse.app.core.model.Workout
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

class FitParser {

    data class FitData(val hr: List<HrSample>, val sessions: List<Workout>)

    fun parse(stream: InputStream): FitData {
        val bytes = stream.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val headerLen = buf.get(0).toInt() and 0xFF
        buf.position(headerLen)

        val tsCache = HashMap<Int, Long>()
        val hr = mutableListOf<HrSample>()
        val sessions = mutableListOf<Workout>()

        while (buf.remaining() >= 2) {
            val headerByte = buf.get().toInt() and 0xFF
            if (headerByte and 0x80 != 0) {
                val timeOffset = buf.get().toInt() and 0xFF
                // timestamp correction record: adjust ms cache
                if (tsCache.containsKey(253)) {
                    val base = tsCache[253] ?: continue
                    val newTs = base + (timeOffset * 1000L)
                    tsCache[253] = newTs
                }
                continue
            }

            val messageType = headerByte and 0x0F
            val hasDeveloper = headerByte and 0x40 != 0
            val numFields = buf.get().toInt() and 0xFF
            var dataLen = 0
            val fields = mutableListOf<Triple<Int, Int, Int>>()
            for (i in 0 until numFields) {
                val fieldNum = buf.get().toInt() and 0xFF
                val fieldSize = buf.get().toInt() and 0xFF
                val baseType = buf.get().toInt() and 0xFF
                fields.add(Triple(fieldNum, fieldSize, baseType))
                dataLen += fieldSize
            }
            if (hasDeveloper) {
                val numDev = buf.get().toInt() and 0xFF
                dataLen += 3 * numDev
            }

            val dataStart = buf.position()
            if (messageType == 0) {
                tsCache[253] = readFieldTimestamp(buf, dataStart, fields) ?: tsCache.getOrDefault(253, 0L)
            } else if (messageType == 20 && fields.any { it.first == 253 }) {
                val ts = readFieldTimestamp(buf, dataStart, fields) ?: tsCache[253] ?: continue
                val hrField = fields.firstOrNull { it.first == 3 }
                if (hrField != null) {
                    val hrIndex = fieldOffset(fields, 3)
                    val bpm = readUint8(buf, dataStart + hrIndex)
                    if (bpm in 25..250) {
                        hr.add(HrSample(Instant.ofEpochSecond(ts), bpm.toDouble(), SourceIdFile))
                    }
                }
                val hrOffset = fieldOffset(fields, 3)
                if (hrOffset >= 0) {
                    readRecordHrSeries(buf, dataStart, fields, ts, hr)
                }
            } else if (messageType == 18 && fields.any { it.first == 253 }) {
                val ts = readFieldTimestamp(buf, dataStart, fields) ?: tsCache[253] ?: continue
                val sportFieldIdx = fields.indexOfFirst { it.first == 5 }
                if (sportFieldIdx >= 0) {
                    val offset = fieldOffset(fields, 5)
                    val sport = readUint8(buf, dataStart + offset)
                    val startTimeField = fields.firstOrNull { it.first == 2 }
                    val startTs = if (startTimeField != null) {
                        readFieldTimestamp(buf, dataStart, fields.filter { it.first == 2 })
                    } else null
                    val totalTimeField = fields.firstOrNull { it.first == 7 }
                    val durationS = if (totalTimeField != null) {
                        val off = fieldOffset(fields, 7)
                        readFloat64Variant(buf, dataStart + off, totalTimeField.third)
                    } else 3600.0
                    val start = Instant.ofEpochSecond(startTs ?: ts)
                    sessions.add(
                        Workout(
                            type = fitSportName(sport),
                            start = start,
                            end = start.plusSeconds(durationS.toLong()),
                            avgHr = null, maxHr = null, calories = null,
                            source = com.pulse.app.core.model.SourceId.FILE_IMPORT
                        )
                    )
                }
            }
            buf.position(dataStart + dataLen)
        }
        return FitData(hr, sessions)
    }

    private fun readRecordHrSeries(
        buf: ByteBuffer, dataStart: Int, fields: List<Triple<Int, Int, Int>>,
        ts: Long, out: MutableList<HrSample>
    ) {
        val hrField = fields.firstOrNull { it.first == 3 } ?: return
        val offset = fieldOffset(fields, 3)
        if (hrField.second > 1) {
            for (i in 0 until hrField.second) {
                val bpm = readUint8(buf, dataStart + offset + i)
                if (bpm in 25..250) {
                    out.add(HrSample(Instant.ofEpochSecond(ts + i), bpm.toDouble(), SourceIdFile))
                }
            }
        }
    }

    private fun fieldOffset(fields: List<Triple<Int, Int, Int>>, fieldNum: Int): Int {
        var offset = 0
        for (f in fields) {
            if (f.first == fieldNum) return offset
            offset += f.second
        }
        return -1
    }

    private fun readUint8(buf: ByteBuffer, at: Int): Int {
        if (at >= buf.capacity()) return 0
        return buf.get(at).toInt() and 0xFF
    }

    private fun readFieldTimestamp(buf: ByteBuffer, dataStart: Int, fields: List<Triple<Int, Int, Int>>): Long? {
        val field = fields.firstOrNull { it.first == 253 } ?: return null
        val off = fieldOffset(fields, 253)
        val at = dataStart + off
        return when (field.third) {
            TYPE_UINT32 -> uintAt(buf, at)
            TYPE_UINT16 -> uintAt(buf, at).toLong()
            else -> null
        }?.takeIf { it > 500_000_000L }
    }

    private fun readFloat64Variant(buf: ByteBuffer, at: Int, baseType: Int): Double = when (baseType) {
        TYPE_UINT16 -> uintAt(buf, at).toDouble()
        TYPE_UINT32 -> uintAt(buf, at).toDouble()
        else -> 3600.0
    }

    private fun uintAt(buf: ByteBuffer, at: Int): Long {
        if (at + 4 > buf.capacity()) return 0
        buf.order(ByteOrder.LITTLE_ENDIAN)
        return (buf.get(at).toLong() and 0xFF) or
            ((buf.get(at + 1).toLong() and 0xFF) shl 8) or
            ((buf.get(at + 2).toLong() and 0xFF) shl 16) or
            ((buf.get(at + 3).toLong() and 0xFF) shl 24)
    }

    private fun fitSportName(sport: Int): String = when (sport) {
        1 -> "running"; 2 -> "cycling"; 5 -> "swimming"; 6 -> "walking"
        10 -> "hiking"; 20 -> "rowing"; 14 -> "strength_training"
        else -> "workout"
    }

    companion object {
        const val TYPE_UINT8 = 2
        const val TYPE_UINT16 = 132
        const val TYPE_UINT32 = 134
        val SourceIdFile = com.pulse.app.core.model.SourceId.FILE_IMPORT
    }
}
